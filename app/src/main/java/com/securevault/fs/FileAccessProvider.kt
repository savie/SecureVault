package com.securevault.fs

/**
 * Metadata ringan untuk satu entri file/folder.
 * Dipakai lintas provider (Standard & Root).
 */
data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * Abstraction layer untuk semua file operation di SecureVault.
 *
 * Implementasi:
 * - StandardFileAccessProvider -> java.io.File (main source set)
 * - RootOperations             -> operasi privileged melalui flavor-specific implementation
 *
 * Aturan:
 * - SEMUA path adalah absolute string.
 * - Method return null kalau operasi tidak applicable (misal: parent of root).
 * - Kalau operasi gagal, return false / empty list — JANGAN throw exception.
 */
interface FileAccessProvider {

    // ── Query ────────────────────────────────────
    fun exists(path: String): Boolean
    fun isDirectory(path: String): Boolean
    fun isFile(path: String): Boolean
    fun canRead(path: String): Boolean
    fun canWrite(path: String): Boolean

    // ── Metadata ─────────────────────────────────
    fun length(path: String): Long
    fun lastModified(path: String): Long
    fun setLastModified(path: String, time: Long): Boolean
    fun parent(path: String): String?
    fun name(path: String): String

    // ── Listing ──────────────────────────────────
    /** Return emptyList() kalau folder kosong / tidak bisa dibaca. */
    fun list(path: String): List<FileInfo>

    // ── Mutation ─────────────────────────────────
    fun mkdirs(path: String): Boolean
    fun delete(path: String): Boolean
    fun deleteRecursively(path: String): Boolean
    fun rename(fromPath: String, toPath: String): Boolean
    fun copy(fromPath: String, toPath: String, overwrite: Boolean): Boolean
}
