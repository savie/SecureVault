package com.securevault.data.repository

import android.content.Context
import android.os.Environment
import com.securevault.data.local.ContainerDbHelper
import com.securevault.data.local.ContainerItem
import com.securevault.utils.ContainerEngine
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

            val passwordHash = password 
            val encryptedPassword = if (enableBiometric) KeystoreHelper.encrypt(password) else null

            dbHelper.insertContainer(
                id, name, file.absolutePath, passwordHash,
                useMasterPassword, enableBiometric, encryptedPassword
            )
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
            val file = File(item.filePath)
            if (file.exists()) file.delete()
            dbHelper.deleteContainer(item.id)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun renameContainer(item: ContainerItem, newName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            if (item.isMounted) return@withContext false // Nggak bisa rename kalo lagi kebuka

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

    // --- MOUNT ---
    suspend fun mountContainer(item: ContainerItem, password: String): File? = withContext(Dispatchers.IO) {
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

    // --- UNMOUNT ---
    suspend fun unmountContainer(item: ContainerItem, password: String): Boolean = withContext(Dispatchers.IO) {
        val tempDir = File(context.filesDir, "containers_temp/${item.id}")
        if (!tempDir.exists()) return@withContext false

        val containerFile = File(item.filePath)
        val success = ContainerEngine.packFolder(tempDir, containerFile, password)
        
        if (success) {
            tempDir.deleteRecursively()
            dbHelper.updateMountStatus(item.id, false)
        }
        success
    }
}
