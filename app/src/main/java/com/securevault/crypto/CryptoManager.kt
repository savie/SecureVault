package com.securevault.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    // Parameter Enkripsi (existing)
    private const val ITERATIONS = 600_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // Parameter Password Hashing (NEW)
    private const val HASH_ITERATIONS = 600_000
    private const val HASH_KEY_LENGTH = 256
    private const val HASH_SALT_LENGTH = 16

    // === EXISTING: Bikin Salt & Derive Key ===
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    // === EXISTING: Encrypt & Decrypt ===
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val cipherText = cipher.doFinal(plaintext)
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        return combined
    }

    fun decrypt(key: SecretKey, encryptedData: ByteArray): ByteArray {
        val iv = encryptedData.copyOfRange(0, IV_LENGTH)
        val cipherText = encryptedData.copyOfRange(IV_LENGTH, encryptedData.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(cipherText)
    }

    // === NEW: Password Hashing (PBKDF2) ===
    // Output: Base64(salt (16 bytes) + hash (32 bytes))
    fun hashPassword(password: String): String {
        val salt = ByteArray(HASH_SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        
        val spec = PBEKeySpec(password.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        spec.clearPassword()
        
        val combined = ByteArray(salt.size + hashBytes.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(hashBytes, 0, combined, salt.size, hashBytes.size)
        
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun verifyPassword(password: String, storedHash: String): Boolean {
        return try {
            val combined = Base64.decode(storedHash, Base64.NO_WRAP)
            if (combined.size < HASH_SALT_LENGTH + 32) return false
            
            val salt = combined.copyOfRange(0, HASH_SALT_LENGTH)
            val expectedHash = combined.copyOfRange(HASH_SALT_LENGTH, combined.size)
            
            val spec = PBEKeySpec(password.toCharArray(), salt, HASH_ITERATIONS, HASH_KEY_LENGTH)
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val actualHash = factory.generateSecret(spec).encoded
            spec.clearPassword()
            
            actualHash.contentEquals(expectedHash)
        } catch (e: Exception) {
            false
        }
    }
}
