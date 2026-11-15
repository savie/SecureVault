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
    val createdAt: Long,
    val usesMasterPassword: Boolean,
    val biometricEnabled: Boolean
)

// Naikin versi DB jadi 2
class ContainerDbHelper(context: Context) : SQLiteOpenHelper(context, "SecureVaultContainers.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE containers (
                id TEXT PRIMARY KEY,
                name TEXT,
                filePath TEXT,
                passwordHash TEXT,
                isMounted INTEGER,
                size INTEGER,
                createdAt INTEGER,
                usesMasterPassword INTEGER,
                biometricEnabled INTEGER,
                encryptedPassword TEXT
            )
        """.trimIndent()
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE containers ADD COLUMN usesMasterPassword INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE containers ADD COLUMN biometricEnabled INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE containers ADD COLUMN encryptedPassword TEXT")
        }
    }

    fun insertContainer(
        id: String, name: String, filePath: String, passwordHash: String,
        usesMasterPassword: Boolean, biometricEnabled: Boolean, encryptedPassword: String?
    ) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", id)
            put("name", name)
            put("filePath", filePath)
            put("passwordHash", passwordHash)
            put("isMounted", 0)
            put("size", 0)
            put("createdAt", System.currentTimeMillis())
            put("usesMasterPassword", if (usesMasterPassword) 1 else 0)
            put("biometricEnabled", if (biometricEnabled) 1 else 0)
            put("encryptedPassword", encryptedPassword)
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
                    createdAt = cursor.getLong(6),
                    usesMasterPassword = cursor.getInt(7) == 1,
                    biometricEnabled = cursor.getInt(8) == 1
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
