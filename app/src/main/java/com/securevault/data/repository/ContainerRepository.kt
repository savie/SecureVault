package com.securevault.data.repository

import android.content.Context
import android.os.Environment
import com.securevault.data.local.ContainerDbHelper
import com.securevault.data.local.ContainerItem
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
            file.writeBytes(ByteArray(0))

            // TODO: Hash password (Fase 5.2). Sementara simpen plaintext.
            val passwordHash = password 
            
            // Kalau biometric enabled, encrypt password pake Keystore
            val encryptedPassword = if (enableBiometric) KeystoreHelper.encrypt(password) else null

            dbHelper.insertContainer(
                id, name, file.absolutePath, passwordHash,
                useMasterPassword, enableBiometric, encryptedPassword
            )
            true
        } catch (e: Exception) {
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
}
