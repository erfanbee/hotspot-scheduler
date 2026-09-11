package com.iranjan.hotspotscheduler.util

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object PasswordCrypto {

    private const val KEY_ALIAS = "hotspot_pw_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

    private val key: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isBlank()) return ""
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv + cipherText, Base64.NO_WRAP)
        } catch (t: Throwable) {
            ""
        }
    }

    fun decrypt(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return try {
            val data = Base64.decode(stored, Base64.NO_WRAP)
            if (data.size <= IV_LENGTH) return stored
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(128, data, 0, IV_LENGTH)
            )
            String(cipher.doFinal(data, IV_LENGTH, data.size - IV_LENGTH), Charsets.UTF_8)
        } catch (t: Throwable) {
            stored
        }
    }
}
