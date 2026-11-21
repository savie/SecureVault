package com.securevault.data.repository

import android.content.Context
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.ContainerDbHelper
import com.securevault.data.local.ContainerItem
import com.securevault.utils.ContainerEngine
import com.securevault.utils.ContainerSessionManager
import com.securevault.utils.KeystoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ContainerRepository(private val context: Context) {
    private val dbHelper = ContainerDbHelper(context)

    companion object {
        private val containerLocks = ConcurrentHashMap<String, Mutex>()

        private fun lockFor(id: String): Mutex =
            containerLocks.getOrPut(id) { Mutex() }
    }

    suspend fun createContainer(
        name: String,
        password: String,
        useMasterPassword: Boolean,
        enableBiometric: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val dir = File(context.filesDir, "containers")
            if (!dir.exists() && !dir.mkdirs()) return@withContext false

            val safeName = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
            val finalSafeName = if (safeName.isEmpty()) "Container" else safeName

            var file = File(dir, "$finalSafeName.svct")
            var counter = 1
            while (file.exists()) {
                file = File(dir, "$finalSafeName ($counter).svct")
                counter++
            }

            val emptyFolder = File(context.cacheDir, "empty_temp_$id")
            if (!emptyFolder.mkdirs()) return@withContext false

            val success = try {
                ContainerEngine.packFolder(emptyFolder, file, password)
            } finally {
                emptyFolder.deleteRecursively()
            }

            if (!success) return@withContext false

            val passwordHash = CryptoManager.hashPassword(password)
            val encryptedPassword =
                if (enableBiometric) KeystoreHelper.encrypt(password) else null

            dbHelper.insertContainer(
                id, name, file.absolutePath, passwordHash,
                useMasterPassword, enableBiometric, encryptedPassword
            )
            dbHelper.updateSize(id, file.length())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun migrateContainersToInternalStorage(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val targetDir = File(context.filesDir, "containers")
                if (!targetDir.exists() && !targetDir.mkdirs()) {
                    return@withContext false
                }

                val targetRoot = targetDir.canonicalFile

                for (item in dbHelper.getAllContainers()) {
                    val source = File(item.filePath)
                    val sourceCanonical = source.canonicalFile

                    val alreadyInternal =
                        sourceCanonical.path == targetRoot.path ||
                            sourceCanonical.path.startsWith(
                                targetRoot.path + File.separator
                            )

                    if (alreadyInternal) {
                        if (!source.isFile) return@withContext false
                        continue
                    }

                    if (!source.isFile) {
                        android.util.Log.e(
                            "SecureVault",
                            "Container migration source missing: ${item.id}"
                        )
                        return@withContext false
                    }

                    val originalName = if (
                        source.name.endsWith(".svct", ignoreCase = true)
                    ) {
                        source.name
                    } else {
                        val safeName = item.name
                            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                            .trim()
                            .ifEmpty { "Container" }
                        "$safeName.svct"
                    }

                    val stem = originalName.substring(
                        0,
                        originalName.length - 5
                    )

                    var candidate = File(targetDir, originalName)
                    var counter = 1

                    while (
                        candidate.exists() &&
                        !filesMatch(source, candidate)
                    ) {
                        candidate = File(
                            targetDir,
                            "$stem ($counter).svct"
                        )
                        counter++
                    }

                    if (!candidate.exists()) {
                        val tempFile = File(
                            targetDir,
                            ".${item.id}.migration.tmp"
                        )

                        if (tempFile.exists()) {
                            if (!tempFile.delete() || tempFile.exists()) {
                                return@withContext false
                            }
                        }

                        try {
                            FileInputStream(source).use { input ->
                                FileOutputStream(tempFile).use { output ->
                                    val buffer = ByteArray(64 * 1024)

                                    while (true) {
                                        val count = input.read(buffer)
                                        if (count < 0) break
                                        output.write(buffer, 0, count)
                                    }

                                    output.fd.sync()
                                }
                            }

                            if (!filesMatch(source, tempFile)) {
                                return@withContext false
                            }

                            android.system.Os.rename(
                                tempFile.absolutePath,
                                candidate.absolutePath
                            )
                        } finally {
                            if (tempFile.exists()) {
                                tempFile.delete()
                            }
                        }
                    }

                    if (!filesMatch(source, candidate)) {
                        return@withContext false
                    }

                    if (
                        !dbHelper.updateFilePath(
                            item.id,
                            candidate.absolutePath
                        )
                    ) {
                        return@withContext false
                    }

                    // Source deletion is allowed only after DB path update.
                    if (source.exists() && !source.delete()) {
                        android.util.Log.w(
                            "SecureVault",
                            "Old Container source retained after migration: ${item.id}"
                        )
                    }
                }

                true
            } catch (e: Exception) {
                android.util.Log.e(
                    "SecureVault",
                    "Container storage migration failed",
                    e
                )
                false
            }
        }

    private fun filesMatch(first: File, second: File): Boolean {
        if (!first.isFile || !second.isFile) return false
        if (first.length() != second.length()) return false

        fun hash(file: File): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")

            FileInputStream(file).use { input ->
                val buffer = ByteArray(64 * 1024)

                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }

            return digest.digest().joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
        }

        return hash(first) == hash(second)
    }

    suspend fun getAllContainers(): List<ContainerItem> =
        withContext(Dispatchers.IO) {
            dbHelper.getAllContainers()
        }

    suspend fun deleteContainer(item: ContainerItem): Boolean =
        withContext(Dispatchers.IO) {
            lockFor(item.id).withLock {
                try {
                    val current = dbHelper.getAllContainers()
                        .find { it.id == item.id }

                    if (current == null || current.isMounted) {
                        return@withLock false
                    }

                    val tempDir = File(
                        context.filesDir,
                        "containers_temp/${item.id}"
                    )

                    if (tempDir.exists()) {
                        tempDir.deleteRecursively()
                        if (tempDir.exists()) return@withLock false
                    }

                    val file = File(current.filePath)
                    if (file.exists()) {
                        if (!file.delete() || file.exists()) {
                            return@withLock false
                        }
                    }

                    dbHelper.deleteContainer(item.id)
                    ContainerSessionManager.removeSession(item.id)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

    suspend fun renameContainer(
        item: ContainerItem,
        newName: String
    ): Boolean = withContext(Dispatchers.IO) {
        lockFor(item.id).withLock {
            try {
                val current = dbHelper.getAllContainers()
                    .find { it.id == item.id } ?: return@withLock false

                if (current.isMounted) return@withLock false

                val oldFile = File(current.filePath)
                val dir = oldFile.parentFile ?: return@withLock false

                val safeName = newName
                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "_")
                    .trim()
                val finalSafeName = if (safeName.isEmpty()) "Container" else safeName

                var newFile = File(dir, "$finalSafeName.svct")
                var counter = 1

                while (newFile.exists()) {
                    newFile = File(dir, "$finalSafeName ($counter).svct")
                    counter++
                }

                if (!oldFile.renameTo(newFile)) return@withLock false

                dbHelper.updateContainer(
                    item.id,
                    newName,
                    newFile.absolutePath
                )
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun mountContainer(
        item: ContainerItem,
        password: String
    ): File? = withContext(Dispatchers.IO) {
        lockFor(item.id).withLock {
            try {
                val current = dbHelper.getAllContainers()
                    .find { it.id == item.id } ?: return@withLock null

                if (current.isMounted) return@withLock null

                if (!CryptoManager.verifyPassword(
                        password,
                        current.passwordHash
                    )
                ) {
                    return@withLock null
                }

                val tempDir = File(
                    context.filesDir,
                    "containers_temp/${item.id}"
                )

                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                    if (tempDir.exists()) return@withLock null
                }

                if (!tempDir.mkdirs()) return@withLock null

                val containerFile = File(current.filePath)

                if (!ContainerEngine.unpackContainer(
                        containerFile,
                        tempDir,
                        password
                    )
                ) {
                    tempDir.deleteRecursively()
                    return@withLock null
                }

                if (!dbHelper.updateMountStatus(item.id, true)) {
                    tempDir.deleteRecursively()
                    return@withLock null
                }

                tempDir
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    suspend fun unmountContainer(
        item: ContainerItem,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        lockFor(item.id).withLock {
            try {
                val current = dbHelper.getAllContainers()
                    .find { it.id == item.id } ?: return@withLock false

                if (!current.isMounted) return@withLock false

                if (!CryptoManager.verifyPassword(
                        password,
                        current.passwordHash
                    )
                ) {
                    return@withLock false
                }

                val tempDir = File(
                    context.filesDir,
                    "containers_temp/${item.id}"
                )

                if (!tempDir.isDirectory) return@withLock false

                val containerFile = File(current.filePath)

                if (!ContainerEngine.packFolder(
                        tempDir,
                        containerFile,
                        password
                    )
                ) {
                    return@withLock false
                }

                // Plaintext must be completely removed before DB reports locked.
                if (tempDir.exists()) {
                    tempDir.deleteRecursively()
                    if (tempDir.exists()) return@withLock false
                }

                if (!dbHelper.updateMountStatus(item.id, false)) {
                    // Restore the mounted plaintext if DB transition fails.
                    val restored = ContainerEngine.unpackContainer(
                        containerFile,
                        tempDir,
                        password
                    )
                    if (!restored) {
                        android.util.Log.e(
                            "SecureVault",
                            "Failed to restore Container plaintext after DB update failure: ${item.id}"
                        )
                    }
                    return@withLock false
                }

                dbHelper.updateSize(item.id, containerFile.length())
                ContainerSessionManager.removeSession(item.id)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    suspend fun getPendingRecoveries(): List<ContainerItem> =
        withContext(Dispatchers.IO) {
            dbHelper.getAllContainers().filter { item ->
                val tempDir = File(
                    context.filesDir,
                    "containers_temp/${item.id}"
                )
                item.isMounted || tempDir.exists()
            }
        }

    suspend fun recoverContainer(
        item: ContainerItem,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        lockFor(item.id).withLock {
            try {
                val current = dbHelper.getAllContainers()
                    .find { it.id == item.id }
                    ?: return@withLock false

                val tempDir = File(
                    context.filesDir,
                    "containers_temp/${item.id}"
                )
                val containerFile = File(current.filePath)

                if (!containerFile.isFile) return@withLock false

                if (!CryptoManager.verifyPassword(
                        password,
                        current.passwordHash
                    )
                ) {
                    return@withLock false
                }

                val hasPlaintext = tempDir.exists()

                if (current.isMounted && hasPlaintext) {
                    if (!tempDir.isDirectory) return@withLock false

                    if (!ContainerEngine.packFolder(
                            tempDir,
                            containerFile,
                            password
                        )
                    ) {
                        return@withLock false
                    }

                    tempDir.deleteRecursively()
                    if (tempDir.exists()) return@withLock false
                } else {
                    // Verify the encrypted Container without touching the
                    // original plaintext or encrypted file.
                    val verifyDir = File(
                        context.cacheDir,
                        "container_recovery_verify_${item.id}"
                    )

                    if (verifyDir.exists()) {
                        verifyDir.deleteRecursively()
                        if (verifyDir.exists()) return@withLock false
                    }

                    if (!verifyDir.mkdirs()) return@withLock false

                    val verified = try {
                        ContainerEngine.unpackContainer(
                            containerFile,
                            verifyDir,
                            password
                        )
                    } finally {
                        verifyDir.deleteRecursively()
                    }

                    if (!verified || verifyDir.exists()) {
                        return@withLock false
                    }

                    // A stale folder with DB status=false may be an
                    // interrupted, incomplete mount. Never pack it.
                    if (!current.isMounted && hasPlaintext) {
                        tempDir.deleteRecursively()
                        if (tempDir.exists()) return@withLock false
                    }
                }

                if (!dbHelper.updateMountStatus(current.id, false)) {
                    if (current.isMounted && hasPlaintext) {
                        val restored = ContainerEngine.unpackContainer(
                            containerFile,
                            tempDir,
                            password
                        )

                        if (!restored) {
                            android.util.Log.e(
                                "SecureVault",
                                "Recovery rollback failed for Container: ${current.id}"
                            )
                        }
                    }
                    return@withLock false
                }

                dbHelper.updateSize(current.id, containerFile.length())
                ContainerSessionManager.removeSession(current.id)
                true
            } catch (e: Exception) {
                android.util.Log.e(
                    "SecureVault",
                    "Container recovery failed: ${item.id}",
                    e
                )
                false
            }
        }
    }

    suspend fun autoLockAll(): Int = withContext(Dispatchers.IO) {
        var lockedCount = 0
        val sessions = ContainerSessionManager.getAllSessions()
        val containers = dbHelper.getAllContainers()

        for (container in containers) {
            if (!container.isMounted) continue
            val password = sessions[container.id] ?: continue

            val locked = lockFor(container.id).withLock {
                try {
                    val current = dbHelper.getAllContainers()
                        .find { it.id == container.id }
                        ?: return@withLock false

                    if (!current.isMounted) {
                        ContainerSessionManager.removeSession(container.id)
                        return@withLock false
                    }

                    val tempDir = File(context.filesDir, "containers_temp/${container.id}")
                    if (!tempDir.isDirectory) return@withLock false

                    val containerFile = File(current.filePath)
                    if (!ContainerEngine.packFolder(tempDir, containerFile, password)) {
                        return@withLock false
                    }

                    if (tempDir.exists()) {
                        val deleted = tempDir.deleteRecursively()
                        if (!deleted || tempDir.exists()) return@withLock false
                    }

                    if (!dbHelper.updateMountStatus(container.id, false)) {
                        val restored = ContainerEngine.unpackContainer(
                            containerFile,
                            tempDir,
                            password
                        )
                        if (!restored) {
                            android.util.Log.e(
                                "ContainerRepository",
                                "CRITICAL: failed to restore plaintext after database update failure"
                            )
                        }
                        return@withLock false
                    }

                    dbHelper.updateSize(container.id, containerFile.length())
                    ContainerSessionManager.removeSession(container.id)
                    true
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }

            if (locked) lockedCount++
        }

        lockedCount
    }
}
