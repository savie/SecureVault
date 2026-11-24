package com.securevault.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

class CryptoStreamV3Test {
    private val password = "SecureVault-Test-Password".toCharArray()
    private val salt = ByteArray(16) { it.toByte() }

    private fun encrypt(
        data: ByteArray,
        format: CryptoStreamV3.Format = CryptoStreamV3.Format.VAULT
    ): ByteArray {
        val output = ByteArrayOutputStream()
        CryptoStreamV3.encryptWithSaltForTest(
            format,
            password.copyOf(),
            salt,
            ByteArrayInputStream(data),
            output
        )
        return output.toByteArray()
    }

    private fun decrypt(
        encrypted: ByteArray,
        output: ByteArrayOutputStream = ByteArrayOutputStream(),
        pass: CharArray = password.copyOf(),
        format: CryptoStreamV3.Format = CryptoStreamV3.Format.VAULT
    ): Boolean {
        return CryptoStreamV3.decrypt(
            format,
            pass,
            ByteArrayInputStream(encrypted),
            output
        )
    }

    @Test
    fun deterministicVaultTestVectorMatchesReference() {
        val plaintext =
            "SecureVault V3 deterministic test vector".toByteArray()

        val encrypted = encrypt(plaintext)
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(encrypted)
            .joinToString("") { "%02x".format(it) }

        assertEquals(182, encrypted.size)
        assertEquals(
            "6b5ff454c82a1e2e6f24f1bd424b1b006532b8d5018f096f979f9273111b8278",
            hash
        )

        val output = ByteArrayOutputStream()
        assertTrue(decrypt(encrypted, output))
        assertTrue(plaintext.contentEquals(output.toByteArray()))
    }

    @Test
    fun supportsEmptyAndChunkBoundaryStreams() {
        val sizes = listOf(
            0,
            1,
            CryptoStreamV3.CHUNK_SIZE - 1,
            CryptoStreamV3.CHUNK_SIZE,
            CryptoStreamV3.CHUNK_SIZE + 1,
            CryptoStreamV3.CHUNK_SIZE * 2 + 7
        )

        for (size in sizes) {
            val plaintext = ByteArray(size) { (it * 31).toByte() }
            val encrypted = encrypt(plaintext)
            val output = ByteArrayOutputStream()

            assertTrue("size=$size", decrypt(encrypted, output))
            assertTrue(
                "size=$size",
                plaintext.contentEquals(output.toByteArray())
            )
        }
    }

    @Test
    fun supportsContainerDomain() {
        val plaintext = "container domain separation".toByteArray()
        val output = ByteArrayOutputStream()
        val encrypted = encrypt(
            plaintext, CryptoStreamV3.Format.CONTAINER
        )

        assertTrue(
            decrypt(
                encrypted,
                output,
                format = CryptoStreamV3.Format.CONTAINER
            )
        )
        assertTrue(plaintext.contentEquals(output.toByteArray()))

        assertFalse(decrypt(encrypted))
    }

    @Test
    fun rejectsWrongPassword() {
        val encrypted = encrypt("secret".toByteArray())
        assertFalse(
            decrypt(encrypted, pass = "wrong-password".toCharArray())
        )
    }

    @Test
    fun rejectsModifiedHeader() {
        val encrypted = encrypt("header test".toByteArray())
        encrypted[28] = (encrypted[28].toInt() xor 1).toByte()

        assertFalse(decrypt(encrypted))
    }

    @Test
    fun rejectsModifiedCiphertext() {
        val encrypted = encrypt("ciphertext test".toByteArray())
        encrypted[HEADER_SIZE + RECORD_HEADER_SIZE + 1] =
            (encrypted[HEADER_SIZE + RECORD_HEADER_SIZE + 1].toInt() xor 1).toByte()

        assertFalse(decrypt(encrypted))
    }

    @Test
    fun rejectsTruncationAndMissingFinalRecord() {
        val encrypted = encrypt("truncation test".toByteArray())

        assertFalse(decrypt(encrypted.copyOf(encrypted.size - 1)))
        assertFalse(decrypt(encrypted.copyOf(encrypted.size - 77)))
    }

    @Test
    fun rejectsTrailingBytes() {
        val encrypted = encrypt("trailing test".toByteArray())
        assertFalse(decrypt(encrypted + byteArrayOf(0x42)))
    }

    @Test
    fun rejectsReorderedDataRecords() {
        val plaintext = ByteArray(CHUNK_SIZE * 2 + 5) {
            (it * 7).toByte()
        }
        val encrypted = encrypt(plaintext)

        val first = recordRange(encrypted, HEADER_SIZE)
        val second = recordRange(encrypted, first.last + 1)

        val reordered = ByteArrayOutputStream()
        reordered.write(encrypted, 0, HEADER_SIZE)
        reordered.write(encrypted, second.first, second.last - second.first + 1)
        reordered.write(encrypted, first.first, first.last - first.first + 1)
        reordered.write(
            encrypted,
            second.last + 1,
            encrypted.size - second.last - 1
        )

        assertFalse(decrypt(reordered.toByteArray()))
    }

    @Test
    fun rejectsDuplicatedDataRecord() {
        val encrypted = encrypt(ByteArray(CHUNK_SIZE * 2 + 9) { it.toByte() })
        val first = recordRange(encrypted, HEADER_SIZE)

        val duplicated = ByteArrayOutputStream()
        duplicated.write(encrypted, 0, first.last + 1)
        duplicated.write(encrypted, first.first, first.last - first.first + 1)
        duplicated.write(
            encrypted,
            first.last + 1,
            encrypted.size - first.last - 1
        )

        assertFalse(decrypt(duplicated.toByteArray()))
    }

    @Test
    fun rejectsMissingMiddleDataRecord() {
        val encrypted = encrypt(ByteArray(CHUNK_SIZE * 3 + 9) { it.toByte() })
        val first = recordRange(encrypted, HEADER_SIZE)
        val second = recordRange(encrypted, first.last + 1)
        val third = recordRange(encrypted, second.last + 1)

        val missing = ByteArrayOutputStream()
        missing.write(encrypted, 0, HEADER_SIZE)
        missing.write(encrypted, first.first, first.last - first.first + 1)
        missing.write(encrypted, third.first, third.last - third.first + 1)
        missing.write(
            encrypted,
            third.last + 1,
            encrypted.size - third.last - 1
        )

        assertFalse(decrypt(missing.toByteArray()))
    }

    @Test
    fun rejectsFinalRecordCountMismatchWithValidTag() {
        val encrypted = encrypt("final count test".toByteArray())
        val changed = rewriteFinalRecord(encrypted) {
            val buffer = ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN)
            buffer.putLong(0, buffer.getLong(0) + 1L)
        }

        assertFalse(decrypt(changed))
    }

    @Test
    fun rejectsFinalRecordLengthMismatchWithValidTag() {
        val encrypted = encrypt("final length test".toByteArray())
        val changed = rewriteFinalRecord(encrypted) {
            val buffer = ByteBuffer.wrap(it).order(ByteOrder.BIG_ENDIAN)
            buffer.putLong(8, buffer.getLong(8) + 1L)
        }

        assertFalse(decrypt(changed))
    }

    @Test
    fun rejectsFinalRecordDigestMismatchWithValidTag() {
        val encrypted = encrypt("final digest test".toByteArray())
        val changed = rewriteFinalRecord(encrypted) {
            it[16] = (it[16].toInt() xor 1).toByte()
        }

        assertFalse(decrypt(changed))
    }

    @Test
    fun failedFinalAuthenticationLeavesOnlyProvisionalOutput() {
        val plaintext = "provisional plaintext".toByteArray()
        val encrypted = encrypt(plaintext)

        encrypted[encrypted.lastIndex] =
            (encrypted.last().toInt() xor 1).toByte()

        val output = ByteArrayOutputStream()
        assertFalse(decrypt(encrypted, output))

        // The caller must discard this output because decrypt returned false.
        assertTrue(plaintext.contentEquals(output.toByteArray()))
    }

    @Test
    fun clearedV3KeyCannotBeReadAgain() {
        val material = ByteArray(32) { (it + 1).toByte() }
        val expected = material.copyOf()
        val key = V3SecretKey(material)

        material.fill(0)
        val encodedBeforeClear = key.encoded
        assertTrue(expected.contentEquals(encodedBeforeClear))
        encodedBeforeClear.fill(0)

        key.clear()

        var rejected = false
        try {
            key.encoded
        } catch (_: IllegalStateException) {
            rejected = true
        }

        expected.fill(0)
        assertTrue("cleared key must reject getEncoded()", rejected)
        key.clear()
    }

    private fun rewriteFinalRecord(
        encrypted: ByteArray,
        mutation: (ByteArray) -> Unit
    ): ByteArray {
        var offset = HEADER_SIZE
        var finalStart = -1
        var finalEnd = -1

        while (offset < encrypted.size) {
            val range = recordRange(encrypted, offset)
            if ((encrypted[offset].toInt() and 0xFF) == FINAL_RECORD) {
                finalStart = range.first
                finalEnd = range.last
                break
            }
            offset = range.last + 1
        }

        check(finalStart >= 0 && finalEnd == encrypted.lastIndex)

        val recordHeader = encrypted.copyOfRange(
            finalStart, finalStart + RECORD_HEADER_SIZE
        )
        val sequence = ByteBuffer.wrap(recordHeader)
            .order(ByteOrder.BIG_ENDIAN)
            .getLong(1)

        val ciphertext = encrypted.copyOfRange(
            finalStart + RECORD_HEADER_SIZE, finalEnd + 1
        )
        val header = encrypted.copyOfRange(0, HEADER_SIZE)
        val fileSalt = header.copyOfRange(12, 28)
        val key = CryptoManager.deriveV3Key(
            password.copyOf(),
            CryptoStreamV3.Format.VAULT.magic,
            fileSalt
        )
        val nonce = ByteBuffer.allocate(12)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .putLong(sequence)
            .array()

        var finalPlaintext: ByteArray? = null
        var replacement: ByteArray? = null

        try {
            val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            decryptCipher.init(
                Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce)
            )
            decryptCipher.updateAAD(header)
            decryptCipher.updateAAD(recordHeader)
            finalPlaintext = decryptCipher.doFinal(ciphertext)

            mutation(finalPlaintext)

            val encryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
            encryptCipher.init(
                Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, nonce)
            )
            encryptCipher.updateAAD(header)
            encryptCipher.updateAAD(recordHeader)
            replacement = encryptCipher.doFinal(finalPlaintext)

            check(replacement.size == ciphertext.size)

            return encrypted.copyOf().also {
                replacement.copyInto(it, finalStart + RECORD_HEADER_SIZE)
            }
        } finally {
            finalPlaintext?.fill(0)
            replacement?.fill(0)
            ciphertext.fill(0)
            header.fill(0)
            fileSalt.fill(0)
            nonce.fill(0)
            key.clear()
        }
    }

    private fun recordRange(
        bytes: ByteArray,
        start: Int
    ): IntRange {
        val lengthOffset = start + 9
        val length =
            ((bytes[lengthOffset].toInt() and 0xFF) shl 24) or
            ((bytes[lengthOffset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[lengthOffset + 2].toInt() and 0xFF) shl 8) or
            (bytes[lengthOffset + 3].toInt() and 0xFF)

        return start..(start + RECORD_HEADER_SIZE + length - 1)
    }

    private companion object {
        const val HEADER_SIZE = 36
        const val RECORD_HEADER_SIZE = 13
        const val FINAL_RECORD = 0x7F
        const val CHUNK_SIZE = 1024 * 1024
    }
}
