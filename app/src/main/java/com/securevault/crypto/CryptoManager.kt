package com.securevault.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object CryptoManager {
    // Parameter Enkripsi
    private const val ITERATIONS = 600_000 // Standar OWASP biar lambat di brute-force
    private const val KEY_LENGTH = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    // 1. Bikin "bumbu" acak (Salt) biar password lu nggak gampang ditebak
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return salt
    }

    // 2. Ngubah password (String) jadi Kunci Enkripsi 256-bit
    fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val keyBytes = factory.generateSecret(spec).encoded
        spec.clearPassword() // Hapus password dari memori RAM biar aman
        return SecretKeySpec(keyBytes, "AES")
    }

    // 3. Ngunci data (Plaintext -> Ciphertext)
    // Hasilnya nanti: [IV 12 byte] + [Data Terenkripsi + Segel GCM]
    fun encrypt(key: SecretKey, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        val cipherText = cipher.doFinal(plaintext)
        
        // Gabungkan IV dan Ciphertext
        val combined = ByteArray(iv.size + cipherText.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(cipherText, 0, combined, iv.size, cipherText.size)
        
        return combined
    }

    // 4. Buka gembok (Ciphertext -> Plaintext)
    fun decrypt(key: SecretKey, encryptedData: ByteArray): ByteArray {
        // Pisahkan IV dan Ciphertext
        val iv = encryptedData.copyOfRange(0, IV_LENGTH)
        val cipherText = encryptedData.copyOfRange(IV_LENGTH, encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))

        // Kalau password salah atau data dimodifikasi, ini bakal error (AEADBadTagException)
        return cipher.doFinal(cipherText)
    }
}
