package com.securevault.data.repository

import android.content.Context
import android.os.Environment
import com.securevault.data.local.ContainerDbHelper
import com.securevault.data.local.ContainerItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ContainerRepository(private val context: Context) {
    private val dbHelper = ContainerDbHelper(context)

    suspend fun createContainer(name: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val id = UUID.randomUUID().toString()
            val dir = File(Environment.getExternalStorageDirectory(), "SecureVault")
            if (!dir.exists()) dir.mkdirs()

            // ==== SANITIZE NAMA CONTAINER ====
            // Ganti karakter aneh (selain huruf, angka, spasi, _, -) jadi _
            val safeName = name.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").trim()
            // Kalo hasil sanitize kosong (misal input cuma emoji), pake "Container"
            val finalSafeName = if (safeName.isEmpty()) "Container" else safeName

            // ==== HANDLE NAMA FILE BENTROK ====
            var file = File(dir, "$finalSafeName.svct")
            var counter = 1
            while (file.exists()) {
                file = File(dir, "$finalSafeName ($counter).svct")
                counter++
            }

            // Bikin file kosong (Fase 5.1)
            file.writeBytes(ByteArray(0))

            dbHelper.insertContainer(id, name, file.absolutePath, password)
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
