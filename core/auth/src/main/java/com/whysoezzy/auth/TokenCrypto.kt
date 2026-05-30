package com.whysoezzy.auth

import android.content.Context
import android.util.Base64
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager

/**
 * Пошаговое AEAD-шифрование строк через Tink. Keyset хранится в SharedPreferences,
 * сам keyset обёрнут master-ключом из Android Keystore. Имя слота передаётся как
 * associated data — привязывает ciphertext к ключу (нельзя подменить access<->refresh).
 */
internal class TokenCrypto(
    context: Context,
) {
    private val aead: Aead =
        run {
            AeadConfig.register()
            AndroidKeysetManager
                .Builder()
                .withSharedPref(context.applicationContext, KEYSET_NAME, KEYSET_PREF_FILE)
                .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()
                .keysetHandle
                .getPrimitive(Aead::class.java)
        }

    fun encrypt(plaintext: String, aad: String): String =
        Base64.encodeToString(
            aead.encrypt(plaintext.encodeToByteArray(), aad.encodeToByteArray()),
            Base64.NO_WRAP,
        )

    fun decrypt(ciphertext: String, aad: String): String =
        aead
            .decrypt(Base64.decode(ciphertext, Base64.NO_WRAP), aad.encodeToByteArray())
            .decodeToString()

    companion object {
        private const val KEYSET_NAME = "__meeting_token_keyset__"
        private const val KEYSET_PREF_FILE = "meeting_token_keyset_prefs"
        private const val MASTER_KEY_URI = "android-keystore://meeting_token_master_key"
    }
}