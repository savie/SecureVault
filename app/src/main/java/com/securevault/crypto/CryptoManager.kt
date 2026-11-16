package com.securevault.crypto

import android.util.Base64
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    private const val ITERATIONS = 600_000
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    private const val HASH_ITERATIONS = 600_000
    private const val HASH_KEY_LENGTH = 256
    private const val HASH_SALT_LENGTH = 16

    // Chunk size: 1MB. Tiap chunk di-encrypt terpisah (doFinal per chunk).
    private const val CHUNK_SIZE = 1 * 1024 * 1024

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

    // === CHUNK-BASED ENCRYPT (Anti-OOM + Anti-Conscrypt-Bug) ===
    fun encryptStream(key: SecretKey, inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(CHUNK_SIZE)
        val lenBuffer = ByteArray(4)
        var read: Int
        while (inputStream.read(buffer).also { read = it } != -1) {
            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            // doFinal per chunk -> Conscrypt nggak buffer >1MB
            val encrypted = cipher.doFinal(buffer, 0, read)

            // Write: CHUNK_LEN (4B, big-endian) + IV (12B) + CIPHERTEXT+TAG
            lenBuffer[0] = (encrypted.size shr 24).toByte()
            lenBuffer[1] = (encrypted.size shr 16).toByte()
            lenBuffer[2] = (encrypted.size shr 8).toByte()
            lenBuffer[3] = encrypted.size.toByte()
            outputStream.write(lenBuffer)
            outputStream.write(iv)
            outputStream.write(encrypted)
        }
        outputStream.flush()
    }

    // === CHUNK-BASED DECRYPT ===
    fun decryptStream(key: SecretKey, inputStream: InputStream, outputStream: OutputStream): Boolean {
        return try {
            val lenBuffer = ByteArray(4)
            val ivBuffer = ByteArray(IV_LENGTH)
            while (true) {
                // Read CHUNK_LEN
                if (!readFully(inputStream, lenBuffer, 4)) break
                val chunkLen = ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                               ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                               ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                               (lenBuffer[3].toInt() and 0xFF)
                if (chunkLen <= 0) break

                if (!readFully(inputStream, ivBuffer, IV_LENGTH)) return false
                val encrypted = ByteArray(chunkLen)
                if (!readFully(inputStream, encrypted, chunkLen)) return false

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, ivBuffer))
                val decrypted = cipher.doFinal(encrypted)
                outputStream.write(decrypted)
            }
            outputStream.flush()
            true
        } catch (e: AEADBadTagException) {
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun readFully(input: InputStream, buffer: ByteArray, length: Int): Boolean {
        var total = 0
        while (total < length) {
            val n = input.read(buffer, total, length - total)
            if (n < 0) return false
            total += n
        }
        return true
    }

    // === Password Hashing ===
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
        } catch (e: Exception) { false }
    }
}
