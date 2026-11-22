package com.securevault.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class VaultDbHelper(context: Context) : SQLiteOpenHelper(context, "SecureVault.db", null, 4) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE vault_files (
                uuid TEXT PRIMARY KEY,
                originalName TEXT,
                mimeType TEXT,
                fileSize INTEGER,
                timestamp INTEGER,
                originalPath TEXT,
                originalTimestamp INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE vault_files ADD COLUMN originalTimestamp INTEGER DEFAULT 0")
        }
    }

    fun insertFile(
        uuid: String,
        name: String,
        mime: String,
        size: Long,
        path: String,
        originalTimestamp: Long
    ) {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("uuid", uuid)
                put("originalName", name)
                put("mimeType", mime)
                put("fileSize", size)
                put("timestamp", System.currentTimeMillis())
                put("originalPath", path)
                put("originalTimestamp", originalTimestamp)
            }

            val insertedRow = db.insert("vault_files", null, values)
            if (insertedRow == -1L) {
                throw IllegalStateException("Failed to save Vault metadata")
            }
        } finally {
            db.close()
        }
    }

    fun getAllFiles(): List<VaultItem> {
        val list = mutableListOf<VaultItem>()
        val db = readableDatabase

        try {
            db.rawQuery("SELECT * FROM vault_files ORDER BY timestamp DESC", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        list.add(
                            VaultItem(
                                cursor.getString(0),
                                cursor.getString(1),
                                cursor.getString(2),
                                cursor.getLong(3),
                                cursor.getLong(4),
                                cursor.getString(5),
                                cursor.getLong(6)
                            )
                        )
                    } while (cursor.moveToNext())
                }
            }
        } finally {
            db.close()
        }

        return list
    }

    fun getFileByUuid(uuid: String): VaultItem? {
        val db = readableDatabase

        try {
            db.rawQuery("SELECT * FROM vault_files WHERE uuid = ?", arrayOf(uuid)).use { cursor ->
                if (!cursor.moveToFirst()) return null

                return VaultItem(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3),
                    cursor.getLong(4),
                    cursor.getString(5),
                    cursor.getLong(6)
                )
            }
        } finally {
            db.close()
        }
    }

    fun deleteFile(uuid: String): Boolean {
        val db = writableDatabase
        try {
            return db.delete("vault_files", "uuid = ?", arrayOf(uuid)) > 0
        } finally {
            db.close()
        }
    }
}
