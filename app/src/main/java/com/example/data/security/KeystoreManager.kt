package com.example.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Android Keystore-backed credential protection using AES/GCM/NoPadding.
 * Keys are securely stored in the device's hardware-backed Keystore provider.
 * Supports graceful fallback to software key in JVM/Robolectric test environments.
 * Credentials are never logged or exposed in diagnostics.
 */
class KeystoreManager {

    private val isAndroidKeyStoreAvailable: Boolean
    private val keyStore: KeyStore

    init {
        var ks: KeyStore
        var available = true
        try {
            ks = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        } catch (_: Exception) {
            available = false
            ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
        }
        isAndroidKeyStoreAvailable = available
        keyStore = ks
    }

    private var fallbackKey: SecretKey? = null

    private fun getOrCreateSecretKey(): SecretKey {
        if (!isAndroidKeyStoreAvailable) {
            val existing = fallbackKey
            if (existing != null) return existing
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256)
            val newKey = keyGen.generateKey()
            fallbackKey = newKey
            return newKey
        }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEY_STORE
            )
            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()

            keyGenerator.init(keyGenParameterSpec)
            return keyGenerator.generateKey()
        }
        val entry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return entry.secretKey
    }

    fun encrypt(plainText: String?): String? {
        if (plainText.isNullOrEmpty()) return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
            val iv = cipher.iv
            val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            // Prepend IV length and IV to ciphertext
            val combined = ByteArray(1 + iv.size + cipherBytes.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)

            Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    fun decrypt(encryptedBase64: String?): String? {
        if (encryptedBase64.isNullOrEmpty()) return null
        return try {
            val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
            val ivSize = combined[0].toInt()
            val iv = ByteArray(ivSize)
            System.arraycopy(combined, 1, iv, 0, ivSize)

            val cipherBytesSize = combined.size - 1 - ivSize
            val cipherBytes = ByteArray(cipherBytesSize)
            System.arraycopy(combined, 1 + ivSize, cipherBytes, 0, cipherBytesSize)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), spec)

            val plainBytes = cipher.doFinal(cipherBytes)
            String(plainBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val ANDROID_KEY_STORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "notifyr_master_credentials_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
