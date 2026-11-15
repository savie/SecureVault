package com.securevault.utils

import android.content.Context
import com.securevault.data.local.ContainerDbHelper

object VaultImporterHelper {
    // Helper buat ngambil encryptedPassword dari DB (buat biometric unlock)
    fun getEncryptedPassword(context: Context, containerId: String): String? {
        val db = ContainerDbHelper(context)
        val cursor = db.readableDatabase.rawQuery(
            "SELECT encryptedPassword FROM containers WHERE id = ?",
            arrayOf(containerId)
        )
        var result: String? = null
        if (cursor.moveToFirst()) {
            result = cursor.getString(0)
        }
        cursor.close()
        return result
    }
}
