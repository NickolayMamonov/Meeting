@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package dev.whysoezzy.meet.push

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import com.google.crypto.tink.Aead
import com.google.crypto.tink.BinaryKeysetReader
import com.google.crypto.tink.BinaryKeysetWriter
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.config.TinkConfig
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.File
import java.io.InputStream
import java.io.OutputStream

internal interface PushStateStore {
    suspend fun read(): PushStateV1

    suspend fun update(transform: (PushStateV1) -> PushStateV1): PushStateV1
}

/**
 * Only an AEAD ciphertext is persisted in DataStore. The typed ProtoBuf aggregate is
 * encrypted before serialization to disk, and the encrypted Tink keyset is kept beside
 * the state file, protected by an Android Keystore master key.
 */
internal class EncryptedPushStateStore(
    context: Context,
) : PushStateStore {
    private val root = File(context.noBackupFilesDir, "push").apply {
        require(mkdirs() || isDirectory) { "Cannot create push state directory" }
    }
    private val stateFile = File(root, "push_state.pb")
    private val keysetFile = File(root, "push_state_keyset.bin")

    @Volatile
    private var pristine = !stateFile.exists() && !keysetFile.exists()
    private val dataStore: DataStore<EncryptedEnvelope>
    private val aead: Aead

    init {
        TinkConfig.register()
        aead = loadAead()
        migrateUnshippedPlaintextStore(context)
        dataStore = DataStoreFactory.create(
            serializer = EnvelopeSerializer,
            produceFile = { stateFile },
        )
    }

    override suspend fun read(): PushStateV1 {
        val envelope = dataStore.data.first()
        val decoded = decode(envelope)
        if (decoded != null) {
            if (decoded.migrationVersion < PUSH_MIGRATION_VERSION) {
                val migrated = decoded.copy(migrationVersion = PUSH_MIGRATION_VERSION)
                dataStore.updateData { EncryptedEnvelope(ciphertext = encrypt(migrated)) }
                return migrated
            }
            return decoded
        }

        if (pristine && envelope.ciphertext.isEmpty()) {
            val pristine = PushStateV1()
            dataStore.updateData { EncryptedEnvelope(ciphertext = encrypt(pristine)) }
            this.pristine = false
            return pristine
        }

        quarantineStateFile()
        val quarantined = PushStateReducer.suppressCorrupt(PushStateV1())
        dataStore.updateData { EncryptedEnvelope(ciphertext = encrypt(quarantined)) }
        return quarantined
    }

    override suspend fun update(transform: (PushStateV1) -> PushStateV1): PushStateV1 {
        var result: PushStateV1? = null
        dataStore.updateData { envelope ->
            val current = decode(envelope) ?: if (pristine && envelope.ciphertext.isEmpty()) {
                PushStateV1()
            } else {
                PushStateReducer.suppressCorrupt(PushStateV1())
            }
            val next = transform(current).also(PushStateReducer::requireValid)
            result = next
            pristine = false
            EncryptedEnvelope(ciphertext = encrypt(next))
        }
        return checkNotNull(result)
    }

    private fun decode(envelope: EncryptedEnvelope): PushStateV1? {
        if (envelope.formatVersion != PUSH_STATE_VERSION || envelope.ciphertext.isEmpty()) return null
        return runCatching {
            ProtoBuf
                .decodeFromByteArray(
                    PushStateV1.serializer(),
                    aead.decrypt(envelope.ciphertext, ASSOCIATED_DATA),
                ).also(PushStateReducer::requireValid)
        }.getOrNull()
    }

    private fun encrypt(state: PushStateV1): ByteArray =
        aead.encrypt(
            ProtoBuf.encodeToByteArray(PushStateV1.serializer(), state),
            ASSOCIATED_DATA,
        )

    private fun migrateUnshippedPlaintextStore(context: Context) {
        File(context.filesDir, "datastore/pending_push_registration_store.preferences_pb")
            .takeIf(File::exists)
            ?.delete()
    }

    private fun quarantineStateFile() {
        if (!stateFile.exists()) return
        val quarantine = File(
            root,
            "push_state.pb.corrupt.${System.currentTimeMillis()}",
        )
        stateFile.copyTo(quarantine, overwrite = false)
    }

    private fun loadAead(): Aead {
        val masterKey = AndroidKeystoreKmsClient.getOrGenerateNewAeadKey(MASTER_KEY_URI)
        val handle = if (keysetFile.exists()) {
            runCatching {
                KeysetHandle.readWithAssociatedData(
                    BinaryKeysetReader.withFile(keysetFile),
                    masterKey,
                    KEYSET_ASSOCIATED_DATA,
                )
            }.getOrElse {
                quarantineKeyset()
                createKeyset(masterKey)
            }
        } else {
            createKeyset(masterKey)
        }
        return handle.getPrimitive(Aead::class.java)
    }

    private fun createKeyset(masterKey: Aead): KeysetHandle =
        KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM")).also {
            it.writeWithAssociatedData(
                BinaryKeysetWriter.withFile(keysetFile),
                masterKey,
                KEYSET_ASSOCIATED_DATA,
            )
        }

    private fun quarantineKeyset() {
        val quarantine = File(
            root,
            "push_state_keyset.bin.corrupt.${System.currentTimeMillis()}",
        )
        check(keysetFile.renameTo(quarantine)) { "Unable to quarantine corrupt push keyset" }
    }

    private companion object {
        const val MASTER_KEY_URI = "android-keystore://meet.push.state.master.v1"
        val ASSOCIATED_DATA = "meet.push.state.proto.v1".toByteArray(Charsets.UTF_8)
        val KEYSET_ASSOCIATED_DATA =
            "meet.push.state.keyset.v1".toByteArray(Charsets.UTF_8)
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
            ProtoBuf.decodeFromByteArray(
                EncryptedEnvelope.serializer(),
                input.readBytes(),
            )
        }.getOrElse { EncryptedEnvelope(formatVersion = -1) }

    override suspend fun writeTo(t: EncryptedEnvelope, output: OutputStream) {
        output.write(ProtoBuf.encodeToByteArray(EncryptedEnvelope.serializer(), t))
    }
}
