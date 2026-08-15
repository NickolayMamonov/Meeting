package dev.whysoezzy.meet.push

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface PushStateStore {
    suspend fun read(): PushStateV1

    suspend fun update(transform: (PushStateV1) -> PushStateV1): PushStateV1
}

/**
 * The DataStore envelope contains only AEAD ciphertext. The key is held by Android Keystore;
 * the keyset marker is deliberately kept in noBackupFilesDir so this store cannot be restored
 * onto another installation.
 */
internal class EncryptedPushStateStore(
    context: Context,
) : PushStateStore {
    private val root = File(context.noBackupFilesDir, "push")
    private val stateFile = File(root, "push_state.pb")
    private val keysetFile = File(root, "push_state_keyset.bin")
    private val dataStore: DataStore<EncryptedEnvelope>

    init {
        require(root.mkdirs() || root.isDirectory) { "Cannot create push state directory" }
        migrateUnshippedPlaintextStore(context)
        if (!keysetFile.exists()) {
            keysetFile.writeText(KEY_ALIAS, Charsets.UTF_8)
        }
        dataStore = DataStoreFactory.create(
            serializer = EnvelopeSerializer,
            produceFile = { stateFile },
        )
    }

    override suspend fun read(): PushStateV1 =
        dataStore.data
            .map { envelope -> decryptOrSuppressed(envelope) }
            .first()

    override suspend fun update(transform: (PushStateV1) -> PushStateV1): PushStateV1 {
        var result: PushStateV1? = null
        dataStore.updateData { envelope ->
            val current = decryptOrSuppressed(envelope)
            val next = transform(current)
            result = next
            EncryptedEnvelope(
                formatVersion = PUSH_STATE_VERSION,
                ciphertext = encrypt(next),
            )
        }
        return checkNotNull(result)
    }

    private fun decryptOrSuppressed(envelope: EncryptedEnvelope): PushStateV1 =
        if (envelope.formatVersion != PUSH_STATE_VERSION) {
            suppressedCorruptState()
        } else if (envelope.ciphertext.isEmpty()) {
            PushStateV1()
        } else {
            runCatching {
                Json.decodeFromString<PushStateV1>(
                    decrypt(envelope.ciphertext).toString(Charsets.UTF_8),
                ).also { state ->
                    require(state.version == PUSH_STATE_VERSION)
                }
            }.getOrElse {
                suppressedCorruptState()
            }
        }

    private fun suppressedCorruptState(): PushStateV1 =
        PushStateReducer.suppressCorrupt(PushStateV1())

    private fun migrateUnshippedPlaintextStore(context: Context) {
        File(context.filesDir, "datastore/pending_push_registration_store.preferences_pb")
            .takeIf(File::exists)
            ?.delete()
    }

    private companion object {
        const val KEY_ALIAS = "meet.push.state.aead.v1"
        val associatedData = "meet.push.state.v1".toByteArray(Charsets.UTF_8)

        fun key(): SecretKey {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator
                    .getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                    .apply {
                        init(
                            KeyGenParameterSpec
                                .Builder(
                                    KEY_ALIAS,
                                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setRandomizedEncryptionRequired(true)
                                .build(),
                        )
                    }.generateKey()
            }
            return keyStore.getKey(KEY_ALIAS, null) as SecretKey
        }

        fun encrypt(state: PushStateV1): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(12).also(SecureRandom()::nextBytes)
            cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.updateAAD(associatedData)
            return iv + cipher.doFinal(
                Json.encodeToString(PushStateV1.serializer(), state).toByteArray(Charsets.UTF_8),
            )
        }

        fun decrypt(ciphertext: ByteArray): ByteArray {
            require(ciphertext.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(),
                GCMParameterSpec(128, ciphertext.copyOfRange(0, 12)),
            )
            cipher.updateAAD(associatedData)
            return cipher.doFinal(ciphertext.copyOfRange(12, ciphertext.size))
        }
    }
}

@Serializable
private data class EncryptedEnvelope(
    val formatVersion: Int = PUSH_STATE_VERSION,
    val ciphertext: ByteArray = byteArrayOf(),
)

private object EnvelopeSerializer : Serializer<EncryptedEnvelope> {
    override val defaultValue: EncryptedEnvelope = EncryptedEnvelope()

    override suspend fun readFrom(input: InputStream): EncryptedEnvelope =
        runCatching {
            val source = input.readBytes().toString(Charsets.UTF_8)
            val element = Json.decodeFromString<JsonObject>(source)
            require(element.jsonObject.keys == setOf("formatVersion", "ciphertext"))
            Json.decodeFromJsonElement(EncryptedEnvelope.serializer(), element)
        }.getOrElse {
            EncryptedEnvelope(formatVersion = INVALID_FORMAT_VERSION)
        }

    override suspend fun writeTo(t: EncryptedEnvelope, output: OutputStream) {
        output.write(
            Json.encodeToString(EncryptedEnvelope.serializer(), t).toByteArray(Charsets.UTF_8),
        )
    }
}

private const val INVALID_FORMAT_VERSION = -1
