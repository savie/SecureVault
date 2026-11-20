# Bug Report - SecureVault v1.4-fase5.6.1

**Tanggal Audit:** 16 November 2026
**Device:** Poco F6 (Rooted, KernelSU-Next)
**Versi:** v1.4-fase5.6.1-security

---

## 🔴 CRITICAL (Wajib Fix Sebelum Rilis)

### BUG-001: OOM saat Encrypt File Besar (>100MB) [CONFIRMED]
- **Reproduksi:**
  1. Buka File Manager -> pilih file APK 500MB dari folder Download.
  2. Tap lama -> Encrypt to Vault.
- **Expected:** File ter-enkripsi masuk Vault.
- **Actual:** App crash dengan OutOfMemoryError.
- **Logcat:**
  java.lang.OutOfMemoryError: Failed to allocate a 501608592 byte allocation
  at com.securevault.crypto.CryptoManager.encrypt(CryptoManager.kt:46)
  at com.securevault.utils.VaultImporter$encryptFile$2.invokeSuspend(VaultImporter.kt:42)
- **Akar Masalah:**
  CryptoManager.encrypt() pakai cipher.doFinal(plaintext) yang load seluruh file ke RAM.
  File 500MB butuh ~1GB RAM. Android heap limit 512MB.
- **Solusi (direncanakan):** Streaming encryption (chunk-based).
- **Dampak Kode:**
  - CryptoManager.kt (tambah encryptStream & decryptStream)
  - VaultImporter.kt (pakai streaming)
  - VaultRepository.kt (pakai streaming)
  - ContainerEngine.kt (pakai streaming)
  - Format .svlt & .svct berubah (perlu wipe/migrasi)
- **Prioritas:** CRITICAL

---

### BUG-002: OOM saat Restore File Besar (>100MB) dari Vault [SUSPECTED]
- **Reproduksi:**
  1. Punya file 500MB di Vault.
  2. Tap file -> Restore.
- **Expected:** File balik ke folder asal.
- **Actual:** Kemungkinan OOM juga (belum ditest).
- **Akar Masalah:** decryptAndRestoreFile() pakai file.readBytes().
- **Prioritas:** CRITICAL (kemungkinan besar kejadian sama).

---

### BUG-003: OOM saat Mount Container Besar (>100MB total) [SUSPECTED]
- **Reproduksi:**
  1. Container dengan total file 500MB+.
  2. Tap Unlock.
- **Expected:** Container terbuka.
- **Actual:** Kemungkinan OOM di ContainerEngine.unpackContainer.
- **Akar Masalah:** ZipInputStream + readBytes() seluruh container.
- **Prioritas:** CRITICAL (kemungkinan besar kejadian sama).

---

### BUG-004: OOM saat Unmount Container Besar (>100MB total) [CONFIRMED]
- **Reproduksi:**
  1. Container dengan total file 500MB+.
  2. Tap Lock.
- **Expected:** Container terkunci.
- **Actual:** Kemungkinan OOM di ContainerEngine.packFolder.
- **Akar Masalah:** readBytes() seluruh file ZIP.
- **Prioritas:** CRITICAL (kemungkinan besar kejadian sama).

---

### BUG-005: OOM saat Copy File Besar (>100MB) antar Panel [SUSPECTED]
- **Reproduksi:**
  1. File 500MB di panel kiri.
  2. Copy ke panel kanan.
- **Expected:** File ter-copy.
- **Actual:** Kemungkinan OOM di FileOperationHelper.copy().
- **Akar Masalah:** source.copyRecursively + readBytes().
- **Prioritas:** CRITICAL (kemungkinan besar kejadian sama).

---

## 🟡 HIGH (Harus Fix Sebelum Rilis)

_(Belum ada, silakan tambahkan kalau nemu)_

---

## 🟢 MEDIUM (Nice to Fix)

_(Belum ada, silakan tambahkan kalau nemu)_

---

## ⚪ LOW (Opsional)

_(Belum ada, silakan tambahkan kalau nemu)_

---

## 📋 SUMMARY

- **Total Bug:** 5 (1 confirmed, 4 suspected)
- **Status:** Audit in progress
- **Next:** Test BUG-002 s/d BUG-005, tambah bug baru kalau ada.
- **Fix Strategy:** Streaming encryption untuk semua operasi file besar.

---


---

## ✅ FASE 6.0 FIXED (16 November 2026)

**Status:** ALL 5 OOM BUGS RESOLVED

**Root Cause:** Conscrypt (Android crypto provider) untuk AES-GCM mode ENCRYPT punya bug: `cipher.update()` nge-buffer SEMUA input di internal ByteArrayOutputStream sampai `doFinal()`. Jadi streaming tetap OOM.

**Fix Approach:** Chunk-Based GCM
- File dipecah jadi chunk 1MB.
- Tiap chunk di-encrypt terpisah (`doFinal()` per chunk) dengan IV sendiri.
- Conscrypt nggak pernah buffer >1MB.
- Memory peak: ~2MB konstan, berapapun ukuran file.

**Test Results:**
- [x] BUG-001 (Vault Encrypt 500MB): FIXED
- [x] BUG-002 (Vault Restore 500MB): FIXED
- [x] BUG-003 (Container Mount 500MB): FIXED
- [x] BUG-004 (Container Unmount 500MB): FIXED
- [x] BUG-005 (Copy File 500MB): FIXED (was not OOM actually, tapi test lolos)

**Format Change:**
- Vault v2: [MAGIC SVLT][SALT 16B][CHUNK1][CHUNK2]...
- Container v2: [MAGIC SVC2][SALT 16B][CHUNK1][CHUNK2]...
- Tiap chunk: [len 4B][IV 12B][ciphertext + GCM tag]
- File v1 (lama) TIDAK KOMPATIBEL (sesuai ekspektasi, karena masih testing).

**Files Changed:**
- crypto/CryptoManager.kt (encryptStream & decryptStream chunked)
- utils/VaultImporter.kt (pakai streaming)
- data/repository/VaultRepository.kt (pakai streaming)
- utils/ContainerEngine.kt (pakai streaming + double-pass pack/unpack)
- utils/StorageHelper.kt (saveFileFromTemp untuk streaming copy)

---

## 📋 SUMMARY (UPDATED)

- **Total Bug:** 5 (semua FIXED di Fase 6.0)
- **Critical OOM:** RESOLVED
- **Format Compatibility:** v1 -> v2 (breaking change, acceptable untuk testing phase)
- **Next:** Audit bug UI/UX (non-OOM).

---

## 🔐 SECURITY HARDENING — STATUS CHECKPOINT 1.6.7

Track ini terpisah dari daftar bug OOM dan roadmap fitur utama.

### Selesai
- Master Password disimpan terenkripsi menggunakan AES-256-GCM dan Android Keystore.
- Biometric Unlock untuk login SecureVault menggunakan dedicated Keystore key dan CryptoObject.
- Global biometric switch mengendalikan biometric login aplikasi dan Container.
- Biometric Mount tersedia untuk Container dengan manual password.
- Password fallback melalui opsi Use Password.
- Global biometric OFF atau biometric per-Container OFF mengharuskan password.
- Cancel/Back pada prompt Container tidak membuka Container dan tidak memunculkan password dialog otomatis.
- Build Free dan Pro serta instalasi APK berhasil pada pengujian sebelumnya.

### PENDING — BIOMETRIC CONTAINER UNMOUNT
- **Status:** Belum diimplementasikan.
- **Scope:** Container yang menggunakan manual password.
- **Expected:** Biometric dapat digunakan untuk proses Unmount dengan mengikuti global biometric switch dan pengaturan biometric per-Container.
- **Catatan:** Tidak mengubah mekanisme Container encryption/decryption.
- **Prioritas:** Enhancement keamanan berikutnya.

### VERIFIKASI TAMBAHAN
- Login biometric SecureVault: Cancel/Back tetap terkunci; PASS pada checkpoint 1.6.7.
- Pemeriksaan kode menunjukkan ERROR_USER_CANCELED dan ERROR_NEGATIVE_BUTTON tidak memanggil callback unlock.

---
