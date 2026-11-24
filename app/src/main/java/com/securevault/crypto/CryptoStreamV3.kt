package com.securevault.crypto

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Shared authenticated streaming format for SVL3 and SVC3.
 *
 * Decryption writes provisional plaintext to the supplied output.
 * Callers must use a temporary file and publish it only after true is returned.
 */
object CryptoStreamV3 {
    internal const val HEADER_SIZE = 36
    internal const val CHUNK_SIZE = 1024 * 1024
    private const val ITERATIONS = 600_000
    private const val SALT_SIZE = 16
    private const val NONCE_SIZE = 12
    private const val TAG_SIZE = 16
    private const val RECORD_HEADER_SIZE = 13
    private const val DATA_RECORD = 0x01
    private const val FINAL_RECORD = 0x7F
    private const val FINAL_PLAINTEXT_SIZE = 48
    private const val FINAL_CIPHERTEXT_SIZE = FINAL_PLAINTEXT_SIZE + TAG_SIZE

    enum class Format(private val identifier: String) {
        VAULT("SVL3"),
        CONTAINER("SVC3");

        val magic: ByteArray
            get() = identifier.toByteArray(Charsets.US_ASCII)
    }

    fun encrypt(
        format: Format,
        password: CharArray,
        input: InputStream,
        output: OutputStream
    ) {
        val salt = CryptoManager.generateSalt()
        encryptInternal(format, password, salt, input, output)
    }

    /**
     * Deterministic salt entry point for same-module tests only.
     */
    internal fun encryptWithSaltForTest(
        format: Format,
        password: CharArray,
        salt: ByteArray,
        input: InputStream,
        output: OutputStream
    ) {
        require(salt.size == SALT_SIZE)
        encryptInternal(format, password, salt.copyOf(), input, output)
    }

    private fun encryptInternal(
        format: Format,
        password: CharArray,
        salt: ByteArray,
        input: InputStream,
        output: OutputStream
    ) {
        val header = createHeader(format, salt)
        val key = CryptoManager.deriveV3Key(password, format.magic, salt)
        val buffer = ByteArray(CHUNK_SIZE)

        try {
            output.write(header)

            val digest = MessageDigest.getInstance("SHA-256")
            var sequence = 0L
            var totalBytes = 0L
            var eof = false

            while (!eof) {
                var count = 0

                while (count < buffer.size) {
                    val read = input.read(buffer, count, buffer.size - count)

                    if (read < 0) {
                        eof = true
                        break
                    }

                    if (read == 0) {
                        val single = input.read()
                        if (single < 0) {
                            eof = true
                            break
                        }
                        buffer[count++] = single.toByte()
                    } else {
                        count += read
                    }
                }

                if (count == 0) continue

                val nextTotal = Math.addExact(totalBytes, count.toLong())
                val plaintext = buffer.copyOfRange(0, count)
                var encrypted: ByteArray? = null

                try {
                    digest.update(plaintext)
                    encrypted = encryptRecord(
                        key, header, DATA_RECORD, sequence, plaintext
                    )
                    writeRecord(output, DATA_RECORD, sequence, encrypted)
                } finally {
                    plaintext.fill(0)
                    encrypted?.fill(0)
                }

                totalBytes = nextTotal
                sequence = Math.addExact(sequence, 1L)
            }

            val finalDigest = digest.digest()
            val finalPlaintext = ByteBuffer.allocate(FINAL_PLAINTEXT_SIZE)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(sequence)
                .putLong(totalBytes)
                .put(finalDigest)
                .array()
            finalDigest.fill(0)

            var finalCiphertext: ByteArray? = null
            try {
                finalCiphertext = encryptRecord(
                    key, header, FINAL_RECORD, sequence, finalPlaintext
                )
                check(finalCiphertext.size == FINAL_CIPHERTEXT_SIZE)
                writeRecord(output, FINAL_RECORD, sequence, finalCiphertext)
            } finally {
                finalPlaintext.fill(0)
                finalCiphertext?.fill(0)
            }

            output.flush()
        } finally {
            buffer.fill(0)
            salt.fill(0)
            key.clear()
        }
    }

    /**
     * Strictly verifies the entire stream, including the mandatory final record.
     */
    @Suppress("UNREACHABLE_CODE")
    fun decrypt(
        expectedFormat: Format,
        password: CharArray,
        input: InputStream,
        output: OutputStream
    ): Boolean {
        return try {
            val header = ByteArray(HEADER_SIZE)
            if (!readFully(input, header, 0, header.size)) return false
            if (!validateHeader(header, expectedFormat)) return false

            val salt = header.copyOfRange(12, 28)
            try {
                val key = CryptoManager.deriveV3Key(
                    password, expectedFormat.magic, salt
                )

                try {
                    val digest = MessageDigest.getInstance("SHA-256")
                    var expectedSequence = 0L
                    var totalBytes = 0L
                    var dataRecordCount = 0L
                    var previousDataWasPartial = false

                    while (true) {
                        val recordHeader = ByteArray(RECORD_HEADER_SIZE)
                        val first = input.read()
                        if (first < 0) return false

                        recordHeader[0] = first.toByte()
                        if (!readFully(
                                input, recordHeader, 1,
                                RECORD_HEADER_SIZE - 1
                            )
                        ) {
                            return false
                        }

                        val recordBuffer = ByteBuffer.wrap(recordHeader)
                            .order(ByteOrder.BIG_ENDIAN)

                        val type = recordBuffer.get().toInt() and 0xFF
                        val sequence = recordBuffer.long
                        val ciphertextLength = recordBuffer.int

                        if (sequence != expectedSequence) return false
                        if (ciphertextLength < TAG_SIZE) return false

                        if (type == DATA_RECORD) {
                            if (previousDataWasPartial) return false
                            if (ciphertextLength !in
                                (TAG_SIZE + 1)..(CHUNK_SIZE + TAG_SIZE)
                            ) {
                                return false
                            }
                        } else if (type == FINAL_RECORD) {
                            if (ciphertextLength != FINAL_CIPHERTEXT_SIZE) {
                                return false
                            }
                        } else {
                            return false
                        }

                        val ciphertext = ByteArray(ciphertextLength)
                        val plaintext = try {
                            if (!readFully(
                                    input, ciphertext, 0, ciphertext.size
                                )
                            ) {
                                return false
                            }

                            decryptRecord(
                                key, header, recordHeader, sequence, ciphertext
                            ) ?: return false
                        } finally {
                            ciphertext.fill(0)
                        }

                        try {
                            if (type == DATA_RECORD) {
                                if (plaintext.isEmpty() ||
                                    plaintext.size > CHUNK_SIZE
                                ) {
                                    return false
                                }

                                previousDataWasPartial =
                                    plaintext.size < CHUNK_SIZE

                                totalBytes = Math.addExact(
                                    totalBytes, plaintext.size.toLong()
                                )
                                dataRecordCount = Math.addExact(
                                    dataRecordCount, 1L
                                )

                                digest.update(plaintext)
                                output.write(plaintext)

                                if (expectedSequence == Long.MAX_VALUE) {
                                    return false
                                }
                                expectedSequence++
                            } else {
                                if (plaintext.size != FINAL_PLAINTEXT_SIZE) {
                                    return false
                                }

                                val finalBuffer = ByteBuffer.wrap(plaintext)
                                    .order(ByteOrder.BIG_ENDIAN)

                                val expectedCount = finalBuffer.long
                                val expectedLength = finalBuffer.long
                                val expectedDigest = ByteArray(32)
                                finalBuffer.get(expectedDigest)

                                try {
                                    if (expectedCount != dataRecordCount) {
                                        return false
                                    }
                                    if (expectedLength != totalBytes) {
                                        return false
                                    }

                                    val actualDigest = digest.digest()
                                    try {
                                        if (!MessageDigest.isEqual(
                                                expectedDigest, actualDigest
                                            )
                                        ) {
                                            return false
                                        }
                                    } finally {
                                        actualDigest.fill(0)
                                    }
                                } finally {
                                    expectedDigest.fill(0)
                                }

                                if (input.read() != -1) return false

                                output.flush()
                                return true
                            }
                        } finally {
                            plaintext.fill(0)
                        }
                    }

                    false
                } finally {
                    key.clear()
                }
            } finally {
                salt.fill(0)
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun createHeader(
        format: Format,
        salt: ByteArray
    ): ByteArray {
        require(salt.size == SALT_SIZE)

        return ByteBuffer.allocate(HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .put(format.magic)
            .putShort(HEADER_SIZE.toShort())
            .put(1.toByte())
            .put(1.toByte())
            .putInt(ITERATIONS)
            .put(salt)
            .putInt(CHUNK_SIZE)
            .putInt(0)
            .array()
    }

    private fun validateHeader(
        header: ByteArray,
        format: Format
    ): Boolean {
        if (!header.copyOfRange(0, 4).contentEquals(format.magic)) {
            return false
        }

        val buffer = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)

        val headerLength = buffer.getShort(4).toInt() and 0xFFFF
        val revision = header[6].toInt() and 0xFF
        val kdfId = header[7].toInt() and 0xFF
        val iterations = buffer.getInt(8)
        val chunkSize = buffer.getInt(28)
        val flags = buffer.getInt(32)

        return headerLength == HEADER_SIZE &&
            revision == 1 &&
            kdfId == 1 &&
            iterations == ITERATIONS &&
            chunkSize == CHUNK_SIZE &&
            flags == 0
    }

    private fun nonce(sequence: Long): ByteArray =
        ByteBuffer.allocate(NONCE_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(0)
            .putLong(sequence)
            .array()

    private fun createRecordHeader(
        type: Int,
        sequence: Long,
        ciphertextLength: Int
    ): ByteArray =
        ByteBuffer.allocate(RECORD_HEADER_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .put(type.toByte())
            .putLong(sequence)
            .putInt(ciphertextLength)
            .array()

    private fun encryptRecord(
        key: javax.crypto.SecretKey,
        header: ByteArray,
        type: Int,
        sequence: Long,
        plaintext: ByteArray
    ): ByteArray {
        val recordHeader = createRecordHeader(
            type, sequence, plaintext.size + TAG_SIZE
        )

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonceBytes = nonce(sequence)
        try {
            cipher.init(
                Cipher.ENCRYPT_MODE,
                key,
                GCMParameterSpec(128, nonceBytes)
            )
        } finally {
            nonceBytes.fill(0)
        }
        cipher.updateAAD(header)
        cipher.updateAAD(recordHeader)

        return cipher.doFinal(plaintext)
    }

    private fun decryptRecord(
        key: javax.crypto.SecretKey,
        header: ByteArray,
        recordHeader: ByteArray,
        sequence: Long,
        ciphertext: ByteArray
    ): ByteArray? {
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val nonceBytes = nonce(sequence)
            try {
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(128, nonceBytes)
                )
            } finally {
                nonceBytes.fill(0)
            }
            cipher.updateAAD(header)
            cipher.updateAAD(recordHeader)
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            null
        }
    }

    private fun writeRecord(
        output: OutputStream,
        type: Int,
        sequence: Long,
        ciphertext: ByteArray
    ) {
        val recordHeader = createRecordHeader(
            type, sequence, ciphertext.size
        )
        output.write(recordHeader)
        output.write(ciphertext)
    }

    private fun readFully(
        input: InputStream,
        buffer: ByteArray,
        offset: Int,
        length: Int
    ): Boolean {
        var total = 0

        while (total < length) {
            val count = input.read(
                buffer, offset + total, length - total
            )

            if (count < 0) return false

            if (count == 0) {
                val single = input.read()
                if (single < 0) return false
                buffer[offset + total] = single.toByte()
                total++
            } else {
                total += count
            }
        }

        return true
    }
}
