package com.disone.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val sharedPreferences: SharedPreferences = run {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "disone_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences("disone_secure_prefs", Context.MODE_PRIVATE)
        }
    }

    fun saveToken(token: String, expiresInSeconds: Long) {
        sharedPreferences.edit().apply {
            putString(KEY_TOKEN, token)
            putLong(KEY_TOKEN_EXPIRY, System.currentTimeMillis() + (expiresInSeconds * 1000))
            apply()
        }
    }

    fun getToken(): String? {
        val token = sharedPreferences.getString(KEY_TOKEN, null) ?: return null
        val expiry = sharedPreferences.getLong(KEY_TOKEN_EXPIRY, 0)
        return if (System.currentTimeMillis() < expiry) token else null
    }

    fun savePlan(plan: String) {
        sharedPreferences.edit().putString(KEY_PLAN, plan).apply()
    }

    fun getPlan(): String? = sharedPreferences.getString(KEY_PLAN, null)

    fun saveWallet(wallet: String) {
        sharedPreferences.edit().putString(KEY_WALLET, wallet).apply()
    }

    fun getWallet(): String? = sharedPreferences.getString(KEY_WALLET, null)

    fun clear() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "jwt_token"
        private const val KEY_TOKEN_EXPIRY = "jwt_expiry"
        private const val KEY_PLAN = "plan"
        private const val KEY_WALLET = "wallet"
    }
}
