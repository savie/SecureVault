package com.securevault.utils

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object MasterPasswordStore {
    private const val PREFS = "vault_prefs"
    private const val LEGACY_KEY = "master_pwd"
    private const val ENCRYPTED_KEY = "master_pwd_enc"
    private const val KEY_ALIAS = "SecureVaultMasterPasswordKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    sealed class LoadResult {
        data class Loaded(val password: String) : LoadResult()
        object NotSet : LoadResult()
        data class Error(val message: String) : LoadResult()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
        }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(false)
            .build()

        generator.init(spec)
        return generator.generateKey()
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)
        check(keyStore.containsAlias(KEY_ALIAS)) {
            "Master Password encryption key is missing"
        }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    private fun encrypt(password: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())

        val encrypted = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        val combined = cipher.iv + encrypted

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)

        require(combined.size >= IV_LENGTH + 16) {
            "Invalid encrypted password data"
        }

        val iv = combined.copyOfRange(0, IV_LENGTH)
        val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getExistingKey(),
            GCMParameterSpec(TAG_LENGTH_BITS, iv)
        )

        return String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }

    fun loadOrMigrate(context: Context): LoadResult {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return try {
            val encrypted = prefs.getString(ENCRYPTED_KEY, null)

            if (encrypted != null) {
                val password = decrypt(encrypted)

                if (prefs.contains(LEGACY_KEY)) {
                    val removed = prefs.edit().remove(LEGACY_KEY).commit()
                    if (!removed) {
                        return LoadResult.Error(
                            "Unable to finish Master Password migration. Please retry."
                        )
                    }
                }

                LoadResult.Loaded(password)
            } else {
                val legacyPassword = prefs.getString(LEGACY_KEY, null)
                    ?: return LoadResult.NotSet

                val encryptedPassword = encrypt(legacyPassword)

                // Persist encrypted data before removing the legacy plaintext.
                val encryptedSaved = prefs.edit()
                    .putString(ENCRYPTED_KEY, encryptedPassword)
                    .commit()

                if (!encryptedSaved) {
                    return LoadResult.Error(
                        "Unable to save encrypted Master Password. Original data was retained."
                    )
                }

                val legacyRemoved = prefs.edit()
                    .remove(LEGACY_KEY)
                    .commit()

                if (!legacyRemoved) {
                    return LoadResult.Error(
                        "Master Password was encrypted, but migration cleanup failed. Please retry."
                    )
                }

                LoadResult.Loaded(legacyPassword)
            }
        } catch (e: Exception) {
            LoadResult.Error(
                "Unable to access the saved Master Password. Vault data has not been reset."
            )
        }
    }

    fun save(context: Context, password: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        return try {
            val encryptedPassword = encrypt(password)

            val encryptedSaved = prefs.edit()
                .putString(ENCRYPTED_KEY, encryptedPassword)
                .commit()

            if (!encryptedSaved) return false

            if (prefs.contains(LEGACY_KEY)) {
                prefs.edit().remove(LEGACY_KEY).commit()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
