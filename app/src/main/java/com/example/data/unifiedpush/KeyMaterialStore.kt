package com.example.data.unifiedpush

import android.content.Context
import com.example.data.security.KeystoreManager
import java.util.UUID

class KeyMaterialStore(context: Context, private val keystoreManager: KeystoreManager = KeystoreManager()) {
    private val preferences = context.getSharedPreferences("notifyr_installations", Context.MODE_PRIVATE)
    private val keys = context.getSharedPreferences("notifyr_subscription_material", Context.MODE_PRIVATE)

    @Synchronized
    fun installationId(connectionId: String): String {
        preferences.getString(connectionId, null)?.let { return it }
        val id = UUID.randomUUID().toString()
        check(preferences.edit().putString(connectionId, id).commit()) { "Installation identity could not be persisted" }
        return id
    }

    fun protectKeyMaterial(value: String): String? = try {
        keystoreManager.encrypt(value)
    } catch (_: RuntimeException) {
        null
    }

    fun revealKeyMaterial(value: String?): String? = keystoreManager.decrypt(value)

    fun storeKeyMaterial(connectionId: String, publicKey: String, auth: String): Boolean {
        val encryptedPublicKey = protectKeyMaterial(publicKey) ?: return false
        val encryptedAuth = protectKeyMaterial(auth) ?: return false
        return keys.edit()
            .putString("$connectionId.public", encryptedPublicKey)
            .putString("$connectionId.auth", encryptedAuth)
            .commit()
    }

    fun publicKey(connectionId: String): String? = revealKeyMaterial(keys.getString("$connectionId.public", null))

    fun authSecret(connectionId: String): String? = revealKeyMaterial(keys.getString("$connectionId.auth", null))

    fun clearKeyMaterial(connectionId: String): Boolean = keys.edit()
        .remove("$connectionId.public")
        .remove("$connectionId.auth")
        .commit()
}
