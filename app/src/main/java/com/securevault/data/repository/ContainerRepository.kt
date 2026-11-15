package com.securevault.data.repository

import android.content.Context
import android.os.Environment
import com.securevault.crypto.CryptoManager
import com.securevault.data.local.ContainerDbHelper
import com.securevault.data.local.ContainerItem
import com.securevault.utils.ContainerEngine
import com.securevault.utils.ContainerSessionManager
import com.securevault.utils.KeystoreHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ContainerRepository(private val context: Context) {
    private val dbHelper = ContainerDbHelper(context)

    suspend fun createContainer(
        name: String,
        password: String,
        useMasterPassword: Boolean,
        enableBiometric: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val dir = File(Environment.getExternalStorageDirectory(), "SecureVault")
            if (!dir.exists()) dir.mkdirs()

            val safeName = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
            val finalSafeName = if (safeName.isEmpty()) "Container" else safeName

            var file = File(dir, "$finalSafeName.svct")
            var counter = 1
            while (file.exists()) {
                file = File(dir, "$finalSafeName ($counter).svct")
                counter++
            }

            val emptyFolder = File(context.cacheDir, "empty_temp")
            emptyFolder.mkdirs()
            val success = ContainerEngine.packFolder(emptyFolder, file, password)
            emptyFolder.deleteRecursively()

            if (!success) return@withContext false

            val passwordHash = CryptoManager.hashPassword(password)
            val encryptedPassword = if (enableBiometric) KeystoreHelper.encrypt(password) else null

            dbHelper.insertContainer(
                id, name, file.absolutePath, passwordHash,
                useMasterPassword, enableBiometric, encryptedPassword
            )
            // FIX 1: Update size saat create
            dbHelper.updateSize(id, file.length())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllContainers(): List<ContainerItem> = withContext(Dispatchers.IO) {
        dbHelper.getAllContainers()
    }

    suspend fun deleteContainer(item: ContainerItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val current = dbHelper.getAllContainers().find { it.id == item.id }
            if (current?.isMounted == true) return@withContext false

            val tempDir = File(context.filesDir, "containers_temp/${item.id}")
            if (tempDir.exists()) tempDir.deleteRecursively()

            val file = File(item.filePath)
            if (file.exists()) file.delete()

            dbHelper.deleteContainer(item.id)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun renameContainer(item: ContainerItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.isMounted) return@withContext false

            val oldFile = File(item.filePath)
            val dir = oldFile.parentFile ?: return@withContext false

            val safeName = newName.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
            val finalSafeName = if (safeName.isEmpty()) "Container" else safeName

            var newFile = File(dir, "$finalSafeName.svct")
            var counter = 1
            while (newFile.exists()) {
                newFile = File(dir, "$finalSafeName ($counter).svct")
                counter++
            }

            if (!oldFile.renameTo(newFile)) return@withContext false
            dbHelper.updateContainer(item.id, newName, newFile.absolutePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun mountContainer(item: ContainerItem, password: String): File? = withContext(Dispatchers.IO) {
        if (!CryptoManager.verifyPassword(password, item.passwordHash)) {
            return@withContext null
        }

        val tempDir = File(context.filesDir, "containers_temp/${item.id}")
        if (tempDir.exists()) tempDir.deleteRecursively()
        tempDir.mkdirs()

        val containerFile = File(item.filePath)
        val success = ContainerEngine.unpackContainer(containerFile, tempDir, password)
        
        if (success) {
            dbHelper.updateMountStatus(item.id, true)
            tempDir
        } else {
            tempDir.deleteRecursively()
            null
        }
    }

    suspend fun unmountContainer(item: ContainerItem, password: String): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.filesDir, "containers_temp/${item.id}")
        if (!tempDir.exists()) return@withContext false

        val containerFile = File(item.filePath)
        val success = ContainerEngine.packFolder(tempDir, containerFile, password)
        
        if (success) {
            tempDir.deleteRecursively()
            dbHelper.updateMountStatus(item.id, false)
            // FIX 1: Update size saat unmount
            dbHelper.updateSize(item.id, containerFile.length())
        }
        success
    }

    suspend fun autoLockAll(): Int = withContext(Dispatchers.IO) {
        var lockedCount = 0
        val sessions = ContainerSessionManager.getAllSessions()
        val containers = dbHelper.getAllContainers()

        for (c in containers) {
            if (c.isMounted) {
                val pwd = sessions[c.id] ?: continue
                val tempDir = File(context.filesDir, "containers_temp/${c.id}")
                if (!tempDir.exists()) continue

                val containerFile = File(c.filePath)
                val success = ContainerEngine.packFolder(tempDir, containerFile, pwd)
                if (success) {
                    tempDir.deleteRecursively()
                    dbHelper.updateMountStatus(c.id, false)
                    dbHelper.updateSize(c.id, containerFile.length()) // FIX 1: Update size di auto-lock
                    ContainerSessionManager.removeSession(c.id)
                    lockedCount++
                }
            }
        }
        lockedCount
    }
}
