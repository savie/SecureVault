package com.securevault.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricUnlockStore {
    private const val PREFS = "vault_prefs"
    private const val ENABLED = "biometric_enabled"
    private const val ENCRYPTED_TOKEN = "biometric_unlock_token"
    private const val TOKEN_HASH = "biometric_unlock_hash"
    private const val ALIAS = "SecureVaultBiometricUnlockKey"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12

    fun canAuthenticate(context: Context): Boolean {
        return BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun isEnrolled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ENABLED, false)) return false

        val hasData = !prefs.getString(ENCRYPTED_TOKEN, null).isNullOrEmpty() &&
            !prefs.getString(TOKEN_HASH, null).isNullOrEmpty()

        if (!hasData || !hasKey()) {
            disable(context)
            return false
        }
        return true
    }

    private fun keyStore(): KeyStore {
        return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    private fun hasKey(): Boolean {
        return try {
            keyStore().containsAlias(ALIAS)
        } catch (_: Exception) {
            false
        }
    }

    private fun getKey(): SecretKey {
        val key = keyStore().getKey(ALIAS, null)
            ?: throw IllegalStateException("Biometric key is unavailable.")
        return key as SecretKey
    }

    private fun createKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun clearEnrollment(context: Context) {
        try {
            val store = keyStore()
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
        } catch (_: Exception) {
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(ENCRYPTED_TOKEN)
            .remove(TOKEN_HASH)
            .putBoolean(ENABLED, false)
            .commit()
    }

    fun disable(context: Context) {
        clearEnrollment(context)
    }

    fun enroll(
        activity: FragmentActivity,
        callback: (Boolean, String?) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            callback(false, "Strong biometric authentication is unavailable.")
            return
        }

        try {
            clearEnrollment(activity)
            createKey()

            val token = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val tokenHash = MessageDigest.getInstance("SHA-256").digest(token)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Biometric Unlock")
                .setSubtitle("Authenticate to protect Vault access")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        try {
                            val authenticatedCipher =
                                result.cryptoObject?.cipher
                                    ?: throw IllegalStateException("Authentication proof is missing.")

                            val encrypted = authenticatedCipher.doFinal(token)
                            val stored = ByteArray(authenticatedCipher.iv.size + encrypted.size)
                            System.arraycopy(
                                authenticatedCipher.iv, 0,
                                stored, 0, authenticatedCipher.iv.size
                            )
                            System.arraycopy(
                                encrypted, 0,
                                stored, authenticatedCipher.iv.size, encrypted.size
                            )

                            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                            val saved = prefs.edit()
                                .putString(ENCRYPTED_TOKEN, Base64.encodeToString(stored, Base64.NO_WRAP))
                                .putString(TOKEN_HASH, Base64.encodeToString(tokenHash, Base64.NO_WRAP))
                                .putBoolean(ENABLED, true)
                                .commit()

                            if (!saved) {
                                clearEnrollment(activity)
                                callback(false, "Could not save biometric enrollment.")
                            } else {
                                callback(true, "Biometric Unlock enabled.")
                            }
                        } catch (e: Exception) {
                            clearEnrollment(activity)
                            callback(false, e.message ?: "Biometric enrollment failed.")
                        }
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        super.onAuthenticationError(code, message)
                        clearEnrollment(activity)
                        callback(false, message.toString())
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                }
            )

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            clearEnrollment(activity)
            callback(false, e.message ?: "Could not initialize biometric security.")
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val encoded = prefs.getString(ENCRYPTED_TOKEN, null)
                ?: throw IllegalStateException("Biometric enrollment is missing.")
            val expectedHash = prefs.getString(TOKEN_HASH, null)
                ?: throw IllegalStateException("Biometric verifier is missing.")

            val stored = Base64.decode(encoded, Base64.NO_WRAP)
            if (stored.size <= IV_LENGTH) {
                throw IllegalStateException("Biometric enrollment data is invalid.")
            }

            val iv = stored.copyOfRange(0, IV_LENGTH)
            val encrypted = stored.copyOfRange(IV_LENGTH, stored.size)

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(128, iv)
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Authenticate to unlock Vault")
                .setNegativeButtonText("Use Password")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build()

            val prompt = BiometricPrompt(
                activity,
                ContextCompat.getMainExecutor(activity),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        try {
                            val authenticatedCipher =
                                result.cryptoObject?.cipher
                                    ?: throw IllegalStateException("Authentication proof is missing.")

                            val token = authenticatedCipher.doFinal(encrypted)
                            val actualHash = MessageDigest.getInstance("SHA-256").digest(token)
                            val expected = Base64.decode(expectedHash, Base64.NO_WRAP)

                            if (MessageDigest.isEqual(actualHash, expected)) {
                                onSuccess()
                            } else {
                                clearEnrollment(activity)
                                onError("Biometric verification failed. Use your password.")
                            }
                        } catch (e: Exception) {
                            clearEnrollment(activity)
                            onError("Biometric key is invalid. Use your password and enroll again.")
                        }
                    }

                    override fun onAuthenticationError(code: Int, message: CharSequence) {
                        super.onAuthenticationError(code, message)
                        if (code != BiometricPrompt.ERROR_USER_CANCELED &&
                            code != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                        ) {
                            onError(message.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                }
            )

            prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
        } catch (e: Exception) {
            clearEnrollment(activity)
            onError("Biometric unlock is unavailable. Use your password and enroll again.")
        }
    }
}
