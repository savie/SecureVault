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
    private const val GCM_TAG_BYTES = GCM_TAG_LENGTH / 8

    private const val HASH_ITERATIONS = 600_000
    private const val HASH_KEY_LENGTH = 256
    private const val HASH_SALT_LENGTH = 16

    private const val CHUNK_SIZE = 1 * 1024 * 1024

    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey =
        deriveAesKey(password, salt, ITERATIONS)

    /**
     * V3 uses a format-specific PBKDF2 salt to separate Vault and Container keys.
     * The legacy deriveKey() contract remains unchanged.
     */
    internal fun deriveV3Key(
        password: CharArray,
        formatMagic: ByteArray,
        salt: ByteArray
    ): V3SecretKey {
        require(formatMagic.size == 4) { "Invalid V3 format identifier" }
        require(salt.size == SALT_LENGTH) { "Invalid V3 salt length" }

        val domain = "SecureVault/V3/".toByteArray(Charsets.UTF_8)
        val separatedSalt = ByteArray(domain.size + formatMagic.size + salt.size)

        try {
            System.arraycopy(domain, 0, separatedSalt, 0, domain.size)
            System.arraycopy(
                formatMagic, 0, separatedSalt, domain.size, formatMagic.size
            )
            System.arraycopy(
                salt, 0, separatedSalt,
                domain.size + formatMagic.size, salt.size
            )

            val keyBytes = deriveAesKeyMaterial(
                password, separatedSalt, ITERATIONS
            )
            return try {
                V3SecretKey(keyBytes)
            } finally {
                keyBytes.fill(0)
            }
        } finally {
            separatedSalt.fill(0)
        }
    }

    private fun deriveAesKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int
    ): SecretKey {
        val keyBytes = deriveAesKeyMaterial(password, salt, iterations)
        return try {
            SecretKeySpec(keyBytes, "AES")
        } finally {
            keyBytes.fill(0)
        }
    }

    private fun deriveAesKeyMaterial(
        password: CharArray,
        salt: ByteArray,
        iterations: Int
    ): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_LENGTH)
        try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            return factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    /**
     * Legacy Gen1 single-block AES-GCM reader.
     * Input starts with the 12-byte IV, followed by ciphertext and tag.
     * Caller MUST write to a temporary destination and publish it only
     * after this method returns true.
     */
    fun decryptLegacyStream(
        key: SecretKey,
        inputStream: InputStream,
        outputStream: OutputStream
    ): Boolean {
        val iv = ByteArray(IV_LENGTH)
        val buffer = ByteArray(64 * 1024)

        return try {
            if (!readFully(inputStream, iv, 0, iv.size)) return false

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH, iv)
            )

            while (true) {
                val count = inputStream.read(buffer)
                if (count < 0) break
                if (count == 0) continue

                val plaintext = cipher.update(buffer, 0, count)
                if (plaintext != null) {
                    try {
                        outputStream.write(plaintext)
                    } finally {
                        plaintext.fill(0)
                    }
                }
            }

            val finalPlaintext = cipher.doFinal()
            try {
                outputStream.write(finalPlaintext)
                outputStream.flush()
            } finally {
                finalPlaintext.fill(0)
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            iv.fill(0)
            buffer.fill(0)
        }
    }

    fun encryptStream(key: SecretKey, inputStream: InputStream, outputStream: OutputStream) {
        val buffer = ByteArray(CHUNK_SIZE)
        val lenBuffer = ByteArray(4)

        while (true) {
            var read = inputStream.read(buffer)

            if (read == -1) break

            if (read == 0) {
                val singleByte = inputStream.read()
                if (singleByte == -1) break
                buffer[0] = singleByte.toByte()
                read = 1
            }

            val iv = ByteArray(IV_LENGTH)
            SecureRandom().nextBytes(iv)

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val encrypted = cipher.doFinal(buffer, 0, read)

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

    fun decryptStream(key: SecretKey, inputStream: InputStream, outputStream: OutputStream): Boolean {
        return try {
            val lenBuffer = ByteArray(4)
            val ivBuffer = ByteArray(IV_LENGTH)
            var chunkCount = 0

            while (true) {
                val firstByte = inputStream.read()

                if (firstByte == -1) {
                    if (chunkCount == 0) return false
                    break
                }

                lenBuffer[0] = firstByte.toByte()

                if (!readFully(inputStream, lenBuffer, 1, 3)) return false

                val chunkLen =
                    ((lenBuffer[0].toInt() and 0xFF) shl 24) or
                    ((lenBuffer[1].toInt() and 0xFF) shl 16) or
                    ((lenBuffer[2].toInt() and 0xFF) shl 8) or
                    (lenBuffer[3].toInt() and 0xFF)

                if (chunkLen < GCM_TAG_BYTES + 1 ||
                    chunkLen > CHUNK_SIZE + GCM_TAG_BYTES
                ) return false

                if (!readFully(inputStream, ivBuffer, 0, IV_LENGTH)) return false

                val encrypted = ByteArray(chunkLen)
                if (!readFully(inputStream, encrypted, 0, chunkLen)) return false

                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_LENGTH, ivBuffer)
                )

                val decrypted = cipher.doFinal(encrypted)
                if (decrypted.isEmpty() || decrypted.size > CHUNK_SIZE) return false

                outputStream.write(decrypted)
                chunkCount++
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

    fun verifyEncryptedStream(
        key: SecretKey,
        inputStream: InputStream,
        expectedSize: Long
    ): Boolean {
        if (expectedSize <= 0L) return false

        val counter = CountingOutputStream()
        val valid = decryptStream(key, inputStream, counter)

        return valid && counter.count == expectedSize
    }

    private fun readFully(
        input: InputStream,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Boolean {
        var total = 0

        while (total < length) {
            val n = input.read(buffer, offset + total, length - total)
            if (n < 0) return false
            if (n == 0) continue
            total += n
        }

        return true
    }

    private class CountingOutputStream : OutputStream() {
        var count: Long = 0
            private set

        override fun write(value: Int) {
            count++
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            count += length.toLong()
        }
    }

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

/**
 * Clearable in-memory key material for V3 operations.
 * Crypto providers may create their own internal copies when initialized.
 */
internal class V3SecretKey(keyBytes: ByteArray) : SecretKey {
    @Volatile
    private var keyMaterial: ByteArray? = keyBytes.copyOf()

    override fun getAlgorithm(): String = "AES"

    override fun getFormat(): String = "RAW"

    @Synchronized
    override fun getEncoded(): ByteArray {
        return keyMaterial?.copyOf()
            ?: throw IllegalStateException("V3 key has been cleared")
    }

    @Synchronized
    fun clear() {
        keyMaterial?.fill(0)
        keyMaterial = null
    }
}
