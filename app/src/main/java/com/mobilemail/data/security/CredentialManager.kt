package com.mobilemail.data.security

import android.content.Context

class CredentialManager(private val context: Context) {
    private val secureStore = KeystoreSecureStore(
        context = context,
        prefsName = "credentials_v2",
        keyAlias = "mobilemail_credentials_key"
    )
    
    fun saveCredentials(server: String, email: String, password: String) {
        secureStore.putString("server_$email", server)
        secureStore.putString("email_$email", email)
        secureStore.putString("password_$email", password)
    }
    
    fun saveTotpCode(email: String, totpCode: String) {
        secureStore.putString("totp_$email", totpCode)
    }
    
    fun getPassword(email: String): String? {
        return secureStore.getString("password_$email")
    }
    
    fun getTotpCode(email: String): String? {
        return secureStore.getString("totp_$email")
    }
    
    fun getServer(email: String): String? {
        return secureStore.getString("server_$email")
    }
    
    fun clearCredentials(email: String) {
        secureStore.remove(
            "server_$email",
            "email_$email",
            "password_$email",
            "totp_$email"
        )
    }
    
    fun clearAllCredentials() {
        secureStore.clear()
    }
}
