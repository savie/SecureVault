package com.securevault.data.repository

import android.content.Context
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.ContainerDbHelper
import com.securevault.data.local.ContainerItem
import com.securevault.utils.ContainerEngine
import com.securevault.utils.AutoLockDiagnosticsStore
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

            val removeUnregisteredArchive = {
                runCatching {
                    !file.exists() || (file.delete() && !file.exists())
                }.getOrDefault(false)
            }

            val emptyFolder = File(context.cacheDir, "empty_temp_$id")
            if (!emptyFolder.mkdirs()) return@withContext false

            val success = try {
                ContainerEngine.packFolder(emptyFolder, file, password)
            } catch (e: Exception) {
                android.util.Log.e(
                    "ContainerRepository",
                    "Container archive creation threw an exception: $id",
                    e
                )
                false
            } finally {
                emptyFolder.deleteRecursively()
            }

            if (!success) {
                if (!removeUnregisteredArchive()) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Failed to remove incomplete archive: $id"
                    )
                }
                return@withContext false
            }

            val credentials = try {
                CryptoManager.hashPassword(password) to
                    if (enableBiometric) KeystoreHelper.encrypt(password) else null
            } catch (e: Exception) {
                android.util.Log.e(
                    "ContainerRepository",
                    "Container credential preparation failed: $id",
                    e
                )
                if (!removeUnregisteredArchive()) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Failed to remove archive after credential preparation failure: $id"
                    )
                }
                return@withContext false
            }

            val passwordHash = credentials.first
            val encryptedPassword = credentials.second

            val inserted = try {
                dbHelper.insertContainer(
                    id,
                    name,
                    file.absolutePath,
                    passwordHash,
                    useMasterPassword,
                    enableBiometric,
                    encryptedPassword,
                    file.length()
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "ContainerRepository",
                    "Container metadata insert threw an exception: $id",
                    e
                )
                false
            }

            if (!inserted) {
                if (!removeUnregisteredArchive()) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Failed to remove archive after metadata insert failure: $id"
                    )
                }
                return@withContext false
            }

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
                    val encryptedPassword =
                        dbHelper.getEncryptedPassword(current.id)

                    if (!dbHelper.deleteContainer(current.id)) {
                        return@withLock false
                    }

                    val archiveDeleted = try {
                        !file.exists() || (file.delete() && !file.exists())
                    } catch (_: Exception) {
                        false
                    }

                    if (!archiveDeleted) {
                        val restored = try {
                            dbHelper.restoreContainer(
                                current,
                                encryptedPassword
                            )
                        } catch (_: Exception) {
                            false
                        }

                        if (!restored) {
                            android.util.Log.e(
                                "ContainerRepository",
                                "Container metadata rollback failed: ${current.id}"
                            )
                        }

                        return@withLock false
                    }

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

                val databaseUpdated = try {
                    dbHelper.updateContainer(
                        item.id,
                        newName,
                        newFile.absolutePath
                    )
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Container rename metadata update threw an exception: ${item.id}",
                        e
                    )
                    false
                }

                if (!databaseUpdated) {
                    val fileRolledBack = try {
                        newFile.renameTo(oldFile)
                    } catch (_: Exception) {
                        false
                    }

                    if (fileRolledBack) {
                        return@withLock false
                    }

                    val dbAligned = newFile.isFile &&
                        try {
                            dbHelper.updateContainer(
                                item.id,
                                newName,
                                newFile.absolutePath
                            )
                        } catch (e: Exception) {
                            android.util.Log.e(
                                "ContainerRepository",
                                "Container rename DB alignment failed: ${item.id}",
                                e
                            )
                            false
                        }

                    if (!dbAligned) {
                        android.util.Log.e(
                            "ContainerRepository",
                            "Container rename rollback failed: ${item.id}"
                        )
                    }

                    return@withLock dbAligned
                }

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

                ContainerSessionManager.addSession(item.id, password)
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
    ): Boolean = unmountContainerInternal(item) { password }

    suspend fun unmountContainerUsingSession(
        item: ContainerItem
    ): Boolean = unmountContainerInternal(item) {
        ContainerSessionManager.getPassword(item.id)
    }

    private suspend fun unmountContainerInternal(
        item: ContainerItem,
        passwordProvider: () -> String?
    ): Boolean = withContext(Dispatchers.IO) {
        lockFor(item.id).withLock {
            try {
                val current = dbHelper.getAllContainers()
                    .find { it.id == item.id } ?: return@withLock false

                if (!current.isMounted) return@withLock false

                // Resolve the secret only after acquiring the container lock.
                val password = passwordProvider() ?: return@withLock false

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

                // The encrypted archive is complete and verified.
                // Persist the locked state before deleting plaintext.
                if (!dbHelper.updateMountStatus(item.id, false, containerFile.length())) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Database lock transition failed; plaintext retained: ${item.id}"
                    )
                    return@withLock false
                }


                ContainerSessionManager.removeSession(item.id)

                // If cleanup is interrupted, startup recovery will verify
                // the encrypted archive and remove the remaining plaintext.
                if (tempDir.exists()) {
                    val deleted = tempDir.deleteRecursively()
                    if (!deleted || tempDir.exists()) {
                        android.util.Log.e(
                            "ContainerRepository",
                            "Container is locked; plaintext cleanup is pending: ${item.id}"
                        )
                    }
                }

                true
            } catch (e: Exception) {
                android.util.Log.e(
                    "ContainerRepository",
                    "Container unmount failed: ${item.id}",
                    e
                )
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
                    // A mounted container still has its complete plaintext.
                    // Pack it before changing the persistent mount state.
                    if (!tempDir.isDirectory) return@withLock false

                    if (!ContainerEngine.packFolder(
                            tempDir,
                            containerFile,
                            password
                        )
                    ) {
                        return@withLock false
                    }
                } else {
                    // A locked container may have leftover plaintext from
                    // an interrupted cleanup. Verify the archive first.
                    val verifyDir = File(
                        context.cacheDir,
                        "container_recovery_verify_${item.id}"
                    )

                    if (verifyDir.exists()) {
                        verifyDir.deleteRecursively()
                        if (verifyDir.exists()) return@withLock false
                    }


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
                }

                // Do not remove plaintext until the locked state is durable.
                if (!dbHelper.updateMountStatus(current.id, false, containerFile.length())) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Recovery database transition failed; plaintext retained: ${current.id}"
                    )
                    return@withLock false
                }


                ContainerSessionManager.removeSession(current.id)

                if (tempDir.exists()) {
                    val deleted = tempDir.deleteRecursively()
                    if (!deleted || tempDir.exists()) {
                        android.util.Log.e(
                            "ContainerRepository",
                            "Recovery verified archive but plaintext cleanup is pending: ${current.id}"
                        )
                        return@withLock false
                    }
                }

                true
            } catch (e: Exception) {
                android.util.Log.e(
                    "ContainerRepository",
                    "Container recovery failed: ${item.id}",
                    e
                )
                false
            }
        }
    }

    suspend fun autoLockAll(): Int = withContext(Dispatchers.IO) {
        var lockedCount = 0
        val containers = dbHelper.getAllContainers()

        AutoLockDiagnosticsStore.record(
            context,
            "REPOSITORY_SCAN",
            "containerCount=${containers.size}"
        )

        for (container in containers) {
            val containerTag = container.id.take(8)

            val locked = lockFor(container.id).withLock {
                try {
                    val current = dbHelper.getAllContainers()
                        .find { it.id == container.id }
                        ?: run {
                            AutoLockDiagnosticsStore.record(
                                context,
                                "CONTAINER_RECORD_MISSING",
                                containerTag
                            )
                            return@withLock false
                        }

                    if (!current.isMounted) {
                        ContainerSessionManager.removeSession(container.id)
                        AutoLockDiagnosticsStore.record(
                            context,
                            "CONTAINER_NOT_MOUNTED",
                            containerTag
                        )
                        return@withLock false
                    }

                    val password = ContainerSessionManager.getPassword(container.id)
                        ?: run {
                            AutoLockDiagnosticsStore.record(
                                context,
                                "SESSION_PASSWORD_MISSING",
                                containerTag
                            )
                            return@withLock false
                        }

                    val tempDir = File(
                        context.filesDir,
                        "containers_temp/${container.id}"
                    )

                    if (!tempDir.isDirectory) {
                        AutoLockDiagnosticsStore.record(
                            context,
                            "PLAINTEXT_DIRECTORY_MISSING",
                            containerTag
                        )
                        return@withLock false
                    }

                    val containerFile = File(current.filePath)

                    AutoLockDiagnosticsStore.record(
                        context,
                        "PACK_STARTED",
                        "container=$containerTag files=${tempDir.walkTopDown().count { it.isFile }}"
                    )

                    if (!ContainerEngine.packFolder(
                            tempDir,
                            containerFile,
                            password
                        ) { event, detail ->
                            AutoLockDiagnosticsStore.record(
                                context,
                                event,
                                "container=$containerTag $detail"
                            )
                        }
                    ) {
                        AutoLockDiagnosticsStore.record(
                            context,
                            "PACK_FAILED",
                            containerTag
                        )
                        return@withLock false
                    }

                    AutoLockDiagnosticsStore.record(
                        context,
                        "PACK_SUCCEEDED",
                        "container=$containerTag size=${containerFile.length()}"
                    )

                    // Persist Locked before deleting the plaintext directory.
                    if (!dbHelper.updateMountStatus(container.id, false, containerFile.length())) {
                        android.util.Log.e(
                            "ContainerRepository",
                            "Auto-lock database transition failed; plaintext retained: ${container.id}"
                        )
                        AutoLockDiagnosticsStore.record(
                            context,
                            "DB_TRANSITION_FAILED",
                            containerTag
                        )
                        return@withLock false
                    }


                    ContainerSessionManager.removeSession(container.id)

                    if (tempDir.exists()) {
                        val deleted = tempDir.deleteRecursively()
                        if (!deleted || tempDir.exists()) {
                            android.util.Log.e(
                                "ContainerRepository",
                                "Auto-locked; plaintext cleanup is pending: ${container.id}"
                            )
                            AutoLockDiagnosticsStore.record(
                                context,
                                "PLAINTEXT_CLEANUP_FAILED",
                                containerTag
                            )
                        } else {
                            AutoLockDiagnosticsStore.record(
                                context,
                                "PLAINTEXT_CLEANUP_SUCCEEDED",
                                containerTag
                            )
                        }
                    }

                    AutoLockDiagnosticsStore.record(
                        context,
                        "CONTAINER_LOCK_SUCCESS",
                        containerTag
                    )
                    true
                } catch (e: Exception) {
                    android.util.Log.e(
                        "ContainerRepository",
                        "Auto-lock failed: ${container.id}",
                        e
                    )
                    AutoLockDiagnosticsStore.record(
                        context,
                        "CONTAINER_LOCK_EXCEPTION",
                        "container=$containerTag type=${e.javaClass.simpleName}"
                    )
                    false
                }
            }

            if (locked) lockedCount++
        }

        AutoLockDiagnosticsStore.record(
            context,
            "REPOSITORY_FINISHED",
            "lockedCount=$lockedCount scanned=${containers.size}"
        )

        lockedCount
    }

}
