package com.securevault.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class ContainerItem(
    val id: String,
    val name: String,
    val filePath: String,
    val passwordHash: String,
    val isMounted: Boolean,
    val size: Long,
    val createdAt: Long,
    val usesMasterPassword: Boolean,
    val biometricEnabled: Boolean
)

class ContainerDbHelper(context: Context) :
    SQLiteOpenHelper(context, "SecureVaultContainers.db", null, 3) {

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

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        if (oldVersion < 2) {
            db.execSQL(
                "ALTER TABLE containers ADD COLUMN usesMasterPassword INTEGER DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE containers ADD COLUMN biometricEnabled INTEGER DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE containers ADD COLUMN encryptedPassword TEXT"
            )
        }
    }

    fun insertContainer(
        id: String,
        name: String,
        filePath: String,
        passwordHash: String,
        usesMasterPassword: Boolean,
        biometricEnabled: Boolean,
        encryptedPassword: String?,
        size: Long = 0L
    ): Boolean {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("id", id)
                put("name", name)
                put("filePath", filePath)
                put("passwordHash", passwordHash)
                put("isMounted", 0)
                put("size", size)
                put("createdAt", System.currentTimeMillis())
                put("usesMasterPassword", if (usesMasterPassword) 1 else 0)
                put("biometricEnabled", if (biometricEnabled) 1 else 0)
                put("encryptedPassword", encryptedPassword)
            }

            return db.insert("containers", null, values) != -1L
        } finally {
            db.close()
        }
    }

    fun restoreContainer(
        item: ContainerItem,
        encryptedPassword: String?
    ): Boolean {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("id", item.id)
                put("name", item.name)
                put("filePath", item.filePath)
                put("passwordHash", item.passwordHash)
                put("isMounted", if (item.isMounted) 1 else 0)
                put("size", item.size)
                put("createdAt", item.createdAt)
                put(
                    "usesMasterPassword",
                    if (item.usesMasterPassword) 1 else 0
                )
                put(
                    "biometricEnabled",
                    if (item.biometricEnabled) 1 else 0
                )
                put("encryptedPassword", encryptedPassword)
            }

            return db.insert("containers", null, values) != -1L
        } finally {
            db.close()
        }
    }

    fun getEncryptedPassword(id: String): String? {
        val db = readableDatabase

        try {
            db.rawQuery(
                "SELECT encryptedPassword FROM containers WHERE id = ?",
                arrayOf(id)
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                return cursor.getString(0)
            }
        } finally {
            db.close()
        }
    }

    fun getAllContainers(): List<ContainerItem> {
        val list = mutableListOf<ContainerItem>()
        val db = readableDatabase

        try {
            db.rawQuery(
                "SELECT * FROM containers ORDER BY createdAt DESC",
                null
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    do {
                        list.add(
                            ContainerItem(
                                id = cursor.getString(0),
                                name = cursor.getString(1),
                                filePath = cursor.getString(2),
                                passwordHash = cursor.getString(3),
                                isMounted = cursor.getInt(4) == 1,
                                size = cursor.getLong(5),
                                createdAt = cursor.getLong(6),
                                usesMasterPassword = cursor.getInt(7) == 1,
                                biometricEnabled = cursor.getInt(8) == 1
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

    fun updateContainer(
        id: String,
        newName: String,
        newPath: String
    ): Boolean {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("name", newName)
                put("filePath", newPath)
            }

            return db.update(
                "containers",
                values,
                "id = ?",
                arrayOf(id)
            ) == 1
        } finally {
            db.close()
        }
    }

    fun updateFilePath(id: String, newPath: String): Boolean {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("filePath", newPath)
            }

            return db.update(
                "containers",
                values,
                "id = ?",
                arrayOf(id)
            ) == 1
        } finally {
            db.close()
        }
    }

    fun updateMountStatus(
        id: String,
        isMounted: Boolean,
        size: Long? = null
    ): Boolean {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("isMounted", if (isMounted) 1 else 0)
                if (size != null) {
                    put("size", size)
                }
            }

            return db.update(
                "containers",
                values,
                "id = ?",
                arrayOf(id)
            ) == 1
        } finally {
            db.close()
        }
    }

    fun updateSize(id: String, size: Long): Boolean {
        val db = writableDatabase

        try {
            val values = ContentValues().apply {
                put("size", size)
            }

            return db.update(
                "containers",
                values,
                "id = ?",
                arrayOf(id)
            ) == 1
        } finally {
            db.close()
        }
    }

    fun deleteContainer(id: String): Boolean {
        val db = writableDatabase

        try {
            return db.delete(
                "containers",
                "id = ?",
                arrayOf(id)
            ) == 1
        } finally {
            db.close()
        }
    }
}
