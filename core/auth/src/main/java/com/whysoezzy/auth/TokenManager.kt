package com.whysoezzy.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.whysoezzy.network.TokenProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class TokenManager(context: Context) : TokenProvider {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_auth_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _isLoggedInFlow = MutableStateFlow(prefs.getString(KEY_ACCESS_TOKEN, null) != null)
    val isLoggedInFlow: StateFlow<Boolean> = _isLoggedInFlow.asStateFlow()

    fun saveTokens(accessToken: String, refreshToken: String, userId: Long? = null) {
        prefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            userId?.let { putLong(KEY_USER_ID, it) }
            apply()
        }
        _isLoggedInFlow.value = prefs.getString(KEY_ACCESS_TOKEN, null) != null
    }

    override fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
    override fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)

    fun getUserId(): Long? {
        val userId = prefs.getLong(KEY_USER_ID, -1L)
        return if (userId != -1L) userId else null
    }

    fun clearTokens() {
        prefs.edit().clear().apply()
        _isLoggedInFlow.value = false
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER_ID = "user_id"
    }
}
