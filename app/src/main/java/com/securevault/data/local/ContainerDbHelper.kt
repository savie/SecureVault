package com.securevault.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ContainerItem(
    val id: String,
    val name: String,
    val filePath: String,
    val isMounted: Boolean,
    val size: Long,
    val createdAt: Long
)

class ContainerDbHelper(context: Context) : SQLiteOpenHelper(context, "SecureVaultContainers.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE containers (
                id TEXT PRIMARY KEY,
                name TEXT,
                filePath TEXT,
                passwordHash TEXT,
                isMounted INTEGER,
                size INTEGER,
                createdAt INTEGER
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS containers")
        onCreate(db)
    }

    fun insertContainer(id: String, name: String, filePath: String, passwordHash: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("filePath", filePath)
            put("passwordHash", passwordHash)
            put("isMounted", 0)
            put("size", 0)
            put("createdAt", System.currentTimeMillis())
        }
        db.insert("containers", null, values)
        db.close()
    }

    fun getAllContainers(): List<ContainerItem> {
        val list = mutableListOf<ContainerItem>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM containers ORDER BY createdAt DESC", null)
        if (cursor.moveToFirst()) {
            do {
                list.add(ContainerItem(
                    id = cursor.getString(0),
                    name = cursor.getString(1),
                    filePath = cursor.getString(2),
                    isMounted = cursor.getInt(4) == 1,
                    size = cursor.getLong(5),
                    createdAt = cursor.getLong(6)
                ))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    fun deleteContainer(id: String) {
        val db = writableDatabase
        db.delete("containers", "id = ?", arrayOf(id))
        db.close()
    }
}
