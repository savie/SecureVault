package com.securevault.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VaultDbHelper(context: Context) : SQLiteOpenHelper(context, "SecureVault.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE vault_files (
                uuid TEXT PRIMARY KEY,
                originalName TEXT,
                mimeType TEXT,
                fileSize INTEGER,
                timestamp INTEGER,
                originalPath TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS vault_files")
        onCreate(db)
    }

    fun insertFile(uuid: String, name: String, mime: String, size: Long, path: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("uuid", uuid)
            put("originalName", name)
            put("mimeType", mime)
            put("fileSize", size)
            put("timestamp", System.currentTimeMillis())
            put("originalPath", path)
        }
        db.insert("vault_files", null, values)
        db.close()
    }

    fun getAllFiles(): List<VaultItem> {
        val list = mutableListOf<VaultItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM vault_files ORDER BY timestamp DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(VaultItem(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    cursor.getLong(3), cursor.getLong(4), cursor.getString(5)
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    // FUNGSI BARU: Buat Decrypt dari File Manager
    fun getFileByUuid(uuid: String): VaultItem? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM vault_files WHERE uuid = ?", arrayOf(uuid))
        var item: VaultItem? = null
        if (cursor.moveToFirst()) {
            item = VaultItem(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getLong(3), cursor.getLong(4), cursor.getString(5)
            )
        }
        cursor.close()
        db.close()
        return item
    }

    fun deleteFile(uuid: String) {
        val db = writableDatabase
        db.delete("vault_files", "uuid = ?", arrayOf(uuid))
        db.close()
    }
}
