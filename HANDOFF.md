# SecureVault - Handoff untuk AI Assistant

Dibaca PERTAMA KALI oleh AI baru (sesi/akun/AI lain). Setelah baca, konfirmasi + ringkas 3 poin penting. JANGAN langsung patch.

## 0. Peran & Bahasa
- AI pair-programmer untuk SecureVault (Android file manager + Vault + Container)
- Komunikasi user: Indonesia santai, no formalitas
- Kode aplikasi (UI string, identifier): English
- Komentar internal non-UI: boleh Indonesia
- Prinsip: langsung eksekusi, no basa-basi

## 1. Environment (WAJIB)
- OS: Android (bukan desktop)
- IDE: Code2Go (AndroidIDE fork)
- Project: ~/SecureVault
- Stack: Kotlin 1.9+, JVM 17, Gradle 9.6.1, minSdk 26 / targetSdk 34 / compileSdk 34

JANGAN:
- ./gradlew clean (hapus aapt2 ARM64, susah recover)
- Build via tombol play Code2Go (output misleading)
- Hapus ~/.androidide/aapt2
- cp APK ke /sdcard untuk pm install (SELinux block)

LAKUKAN:
- Build: ./gradlew assembleDebug (build free + pro sekaligus)
- Install: su -c "cp APK /data/local/tmp/sv.apk" lalu su -c "pm install -r /data/local/tmp/sv.apk"
- Verify: su -c "dumpsys package com.securevault | grep -E 'versionName|lastUpdateTime'"

Kalau aapt2 error (Exec format error / missing):
curl -L -o "$HOME/aapt2-arm64" "https://github.com/AndroidIDEOfficial/platform-tools/releases/download/v34.0.4/aapt2-arm64-v8a"
cp "$HOME/aapt2-arm64" "$HOME/.androidide/aapt2"
chmod +x "$HOME/.androidide/aapt2"
rm -f "$HOME/aapt2-arm64"

## 2. CARA KERJA — Siklus Dialog User-AI (PENTING)

Ini yang WAJIB dipahami AI baru. Setiap sub-fase mengikuti siklus ini.

### 2.1 Siklus per Sub-Fase

User: minta lanjut / tanya
  ↓
AI: DISKUSI SCOPE dulu (kalau fitur baru/besar) ATAU langsung kasih spec (kalau scope udah jelas)
  ↓
User: ACC scope / kasih koreksi
  ↓
AI: Kasih PATCH (1 blok bash copy-paste) + penjelasan singkat + sanity check + langkah user
  ↓
User: eksekusi di terminal, paste output
  ↓
CABANG:
  ├─ Output OK   → AI kasih langkah build + smoke checklist
  ├─ Output error → AI debug, kasih patch fix, ulangi
  └─ Output aneh  → AI minta data tambahan (cat/grep), JANGAN nebak
  ↓
User: build + smoke test, paste hasil
  ↓
CABANG:
  ├─ Smoke OK    → AI kasih script commit + tag
  ├─ Bug ketemu  → AI analisis, kasih patch fix, ulangi dari build
  └─ Crash/parah → AI saran rollback tag, investigasi
  ↓
User: commit + tag, konfirmasi
  ↓
AI: konfirmasi LOCKED, siap fase berikutnya

### 2.2 Format Respons AI yang BENAR

Setiap patch AI harus punya 5 elemen INI:

1. **Tujuan** — 1-2 baris, apa yang bakal berubah
2. **Scope** — daftar file yang disentuh + apa yang diubah
3. **Terminal block** — 1 blok bash (cd + patch + sanity check), copy-paste ready
4. **Expected output** — apa yang user harus lihat (grep count, wc -l, hash)
5. **Next step** — apa yang user lakuin setelah patch (build/smoke/commit)

### 2.3 Contoh — AI Respons BURUK

"Oke kita ubah FileManagerViewModel, ganti A jadi B, terus update import, sama tambahin X. Coba build. Kalau error kabarin."

Masalah: nggak ada script, nggak ada sanity, user harus nebak.

### 2.4 Contoh — AI Respons BENAR

Tujuan: Wire listing ke provider
Scope: FileManagerViewModel.kt (2 lokasi), tambah import
[terminal block bash copy-paste]
Expected: grep count = 2, wc -l = X
Next: ./gradlew assembleDebug, paste output

### 2.5 Kapan AI Nanya vs Langsung Eksekusi

NANYA dulu (minta data):
- User lapor bug tanpa detail → minta "paste cat file.kt" atau "grep -rn pattern"
- Scope belum jelas → tanya user ekspektasi
- Perubahan besar (>5 file) → diskusi dulu
- Keputusan strategis (fitur, distribusi, Play Store) → diskusi dulu

LANGSUNG EKSEKUSI:
- Bug udah ketrace jelas → fix langsung
- Refactor 1-3 file yang udah ACC
- Sed/awk mechanical
- Lanjutan sub-fase dengan scope yang udah disepakati
- User bilang "lanjut", "gas", "eksekusi"

### 2.6 Trigger User

| User bilang | Arti | AI lakuin |
|---|---|---|
| "Lanjut X" / "Gas" | Scope udah jelas | Kasih patch X |
| "Build hijau" | Patch sukses | Kasih smoke checklist |
| "Smoke OK" / "All good" | Verifikasi sukses | Kasih commit + tag script |
| "Error" / paste error | Build/compile gagal | Debug, kasih fix patch |
| "Bug: [detail]" | Behavior salah | Analisis, kasih fix patch |
| "Crash" | Runtime crash | Minta logcat / stack trace dulu, JANGAN nebak |
| "Stop" / "Diskusi dulu" | Pause | Berhenti patch, diskusi |
| "Rollback" | Kacau | Kasih script git checkout tag |

### 2.7 Kalau AI Ganti Sesi / Kena Limit

User bakal paste HANDOFF.md ini ke AI baru. AI baru WAJIB:
1. Baca sampai habis, terutama bagian 5.3, 6, dan 8
2. Konfirmasi + ringkas 3 poin (environment, cara kerja, workflow)
3. Minta user jalanin: git log -5, git tag, git status
4. Tanya: "lanjut dari mana"
5. Baru mulai siklus normal

JANGAN langsung patch. JANGAN anggap konsisten dengan sesi lama.

### 2.8 Yang Bikin Sesi Rusak (JANGAN DILAKUKAN)

- Patch raksasa multi-file tanpa sanity check
- Nebak signature function tanpa baca file dulu
- Skip smoke test, langsung commit
- Anggap scope "udah jelas" padahal ambigu
- Bilang "sudah selesai" tanpa user konfirmasi smoke OK
- Dump kode mentah (user nggak bisa copy-paste rapi)
- Nested code fence yang bikin terminal rusak parsing

## 3. Format Output
Setiap perubahan kode = terminal command block (bash heredoc/sed/awk), bukan dump kode mentah.
Struktur: judul, penjelasan 2-4 baris, terminal block, sanity check, langkah user (build/smoke/commit/tag).
JANGAN: teori panjang, disclaimer berlebihan.
LAKUKAN: langsung solusi, minta data via cat/grep kalau butuh.

## 4. Workflow per Sub-Fase
1. Scope (diskusi kalau besar/belum jelas)
2. Patch (AI kasih terminal block)
3. User eksekusi
4. Build: ./gradlew assembleDebug
5. Smoke test manual (AI kasih checklist)
6. OK -> commit + tag
7. Error -> paste, AI debug, ulangi
8. Kacau -> rollback via tag

Tag: v1.6.6-d1, v1.6.6-d2, dst
Commit: conventional (feat:, fix:, refactor:, ci:, docs:)
Backup: .bak_<fase> sebelum patch. .gitignore cover *.bak* dan *.tmp*

## 5. Arsitektur
com.securevault/
- MainActivity.kt (nav + auto-lock)
- data/local, data/repository
- fs/ (src/main): FileAccessProvider, StandardFileAccessProvider, RootOperations, FileAccessManager
- ui/screens/ (thin): ContainerScreen, FileManagerScreen, VaultScreen, SettingsScreen, ContainerActionHandler
- ui/viewmodel/: FileManagerViewModel, VaultViewModel, ContainerViewModel
- ui/components/: dialogs/ (ContainerDialogs, FileManagerDialogs, VaultDialogs), ContainerCard, FilePanel, VaultItemCard, MenuItem, AppTopBar
- utils/: FileUiUtils, FileOperationHelper, StorageHelper, VaultImporter, ContainerEngine, KeystoreHelper

app/src/main (shared), app/src/free/java/com/securevault/fs/ (FreeRootOperations, ProviderFactory), app/src/pro/java/com/securevault/fs/ (ProRootOperations, ProviderFactory)

Gradle flavor: free (com.securevault), pro (com.securevault.pro). Root code HANYA di pro/.
Build output: app/build/outputs/apk/{free,pro}/debug/

## 5.1 File Access Architecture (6.6D)

- FileAccessProvider dan StandardFileAccessProvider berada di main source set.
- FileAccessManager menjadi entry point untuk standard provider dan RootOperations.
- RootOperations adalah interface bersama.
- FreeRootOperations dan ProviderFactory berada di free source set.
- ProRootOperations dan ProviderFactory berada di pro source set.
- libsu hanya dideklarasikan sebagai proImplementation.
- FileManagerViewModel menggunakan RootOperations.listWithRoot() untuk root listing.
- Root implementation tidak boleh bocor ke free source set.

## 5.2 Archive Operations (6.7)

- `ArchiveHelper.kt` berada di `app/src/main/java/com/securevault/utils/`.
- Mendukung kompresi dan ekstraksi ZIP serta TAR.GZ melalui Apache Commons Compress.
- Dependency: `commons-compress:1.26.0` dan `commons-codec:1.16.1`.
- Tersedia pada Free dan Pro; tidak menggunakan root.
- Kompresi dilakukan untuk satu file atau folder per operasi.
- Output arsip berada di folder panel aktif, sejajar dengan sumber.
- Format nama output: `[nama].zip` atau `[nama].tar.gz`.
- Jika nama output sudah digunakan, tambahkan suffix `(1)`, `(2)`, dan seterusnya.
- Aksi Compress tersedia melalui long-press file/folder dan bottom sheet.
- Aksi Extract tersedia untuk arsip ZIP dan TAR.GZ.
- Ekstraksi dilakukan langsung di folder induk arsip, bukan subfolder otomatis.
- Struktur folder di dalam arsip dipertahankan. Wrapper folder yang namanya sama dengan nama dasar arsip dilepas.
- Konflik nama file/folder hasil ekstraksi menggunakan suffix `(1)`, `(2)`, tanpa overwrite.
- Validasi path mencegah ZIP Slip/TAR path traversal; symbolic link ditolak.
- Operasi menggunakan streaming I/O dan membersihkan output parsial jika gagal.
- Progress menggunakan indikator loading panel yang sudah tersedia.
- Smoke test manual Free dan Pro: kompresi, ekstraksi, ZIP, TAR.GZ, serta bentrok nama dinyatakan berhasil.

## 5.3 Security Hardening (Cross-Cutting)

Track ini mencatat penguatan keamanan lintas fitur dan tidak menggantikan urutan roadmap utama.

Selesai:
- Master Password tersimpan menggunakan AES-256-GCM dan Android Keystore; migrasi dari plaintext.
- Biometric Unlock untuk login SecureVault menggunakan dedicated Android Keystore key dan CryptoObject.
- Global biometric master switch berlaku untuk login aplikasi dan Container.
- Biometric Mount untuk Container yang menggunakan manual password.
- Password fallback melalui tombol Use Password.
- Global switch OFF atau biometric per-Container OFF memaksa penggunaan password.
- Cancel/Back pada prompt Container tetap mempertahankan kondisi terkunci.
- Build dan instalasi Free serta Pro telah berhasil pada pengujian sebelumnya.

Pending:
- Biometric Unmount untuk Container dengan manual password.
- Retest runtime Cancel/Back pada login SecureVault: PASS pada checkpoint 1.6.7.

Di luar scope saat ini:
- Container yang menggunakan Master Password tetap mengikuti perilaku mount/unmount yang sudah ada.

## 5.4 Container & Vault Storage (6.7-d5)

- File Vault aktif (`*.svlt`) disimpan di internal storage pada `context.filesDir/vault/`.
- File Container aktif (`*.svct`) disimpan di internal storage pada `context.filesDir/containers/`.
- Plaintext Container saat mounted disimpan sementara pada `context.filesDir/containers_temp/{containerId}/`.
- Migrasi D5.6 hanya memindahkan Container lama yang masih tercatat menggunakan lokasi eksternal. Container yang sudah berada di internal storage dilewati.
- Migrasi dijalankan melalui startup gate setelah Master Password berhasil dibuka. Migrasi bukan proses ekspor atau pengumpulan semua file `.svlt` dan `.svct`.
- `/sdcard/SecureVault/` disiapkan sebagai lokasi tujuan fitur Export/Backup untuk salinan file Vault dan Container.
- Fitur Export/Backup belum diimplementasikan dalam D5 dan tetap menjadi pekerjaan terpisah pada roadmap berikutnya.
- File aktif internal dan salinan ekspor/backup eksternal memiliki fungsi berbeda. Jangan mengubah lokasi penyimpanan aktif tanpa audit dan persetujuan user.

## 6. Roadmap Resmi SecureVault

Roadmap ini adalah acuan utama urutan pengembangan SecureVault. Jangan melewati tahapan atau mengubah prioritas tanpa persetujuan user.

### 6.1 Phase 6.7 — Stability & Security

Fokus: menjaga integritas data, keamanan autentikasi, dan stabilitas lifecycle aplikasi.

| Subtask | Deskripsi | Status |
|---|---|---|
| 6.7-d1 | Container Database Migration — memastikan migrasi database tidak menghapus data yang sudah ada | DONE |
| 6.7-d2 | Master Password Security — menyimpan Master Password menggunakan Android Keystore dan AES-256-GCM | DONE |
| 6.7-d3 | Biometric Security Hardening — autentikasi biometrik menggunakan dedicated Keystore key dan CryptoObject | DONE |
| 6.7-d4 | Vault Encryption Integrity — audit dan perbaikan integritas proses enkripsi serta penghapusan source | DONE |
| 6.7-d5 | Auto-Lock Lifecycle — lifecycle, atomic packing, recovery, internal storage migration, dan verifikasi Container | DONE |

Catatan:
- 6.7-d1 sampai 6.7-d3 sudah selesai dan memiliki checkpoint Git masing-masing.
- Biometric Container Unmount untuk Container yang menggunakan manual password masih pending.
- Container yang menggunakan Master Password mempertahankan perilaku yang sudah ada. Jangan mengubahnya tanpa persetujuan.
- 6.7-d4 selesai. `CryptoManager` menerapkan parsing ciphertext yang ketat, autentikasi AES-GCM, dan verifikasi ukuran plaintext tanpa membuat temporary plaintext saat verifikasi enkripsi.
- 6.7-d4: `VaultImporter`, `VaultRepository`, `VaultDbHelper`, `FileHelper`, dan `StorageHelper` diperkuat untuk validasi source, penyimpanan metadata, verifikasi ciphertext, serta penghapusan source yang terkonfirmasi. Kegagalan penghapusan source tidak dilaporkan sebagai keberhasilan penuh.
- 6.7-d4: temporary plaintext pada proses restore `VaultImporter` selalu dibersihkan melalui `finally`, termasuk ketika dekripsi gagal atau terjadi exception.
- 6.7-d4: pengujian runtime Free dan Pro berhasil. Enkripsi menghapus source, restore berhasil, dan SHA-256 hasil restore identik dengan file asli.
- 6.7-d4: Free mengembalikan file ke `Download/SecureVault`, sedangkan Pro mengembalikan file ke folder asal sesuai hasil pengujian.
- 6.7-d4: build `assembleFreeDebug` dan `assembleProDebug` berhasil. Ciphertext tampering belum diuji langsung dengan file yang dimodifikasi; validasi penolakan autentikasi telah diperiksa melalui kode.
- 6.7-d5.1: `ContainerAutoLockCoordinator` diinisialisasi melalui application context dan mengamati `ProcessLifecycleOwner`. Timer dijadwalkan saat proses masuk background, dibatalkan saat kembali foreground, dan mengikuti preferensi `container_auto_lock_ms`.
- 6.7-d5.2: `ContainerEngine.packFolder()` menggunakan temporary file, verifikasi hasil enkripsi melalui dekripsi dan pemeriksaan SHA-256, kemudian atomic rename. Container lama tidak dihapus sebelum hasil baru terverifikasi.
- 6.7-d5.3: Auto-Lock memverifikasi hasil packing, penghapusan plaintext, dan perubahan status database. Jika perubahan database gagal, plaintext dipulihkan dari Container terenkripsi.
- 6.7-d5.4: Startup Recovery menangani Container yang masih tercatat mounted atau memiliki folder plaintext tertinggal setelah process death. Recovery memerlukan password Container manual atau menggunakan Master Password sesuai konfigurasi. Plaintext tidak dihapus sebelum data terenkripsi berhasil diverifikasi.
- 6.7-d5.5: Penyimpanan aktif Container baru dipindahkan ke `context.filesDir/containers/`. Folder plaintext sementara berada di `context.filesDir/containers_temp/`.
- 6.7-d5.6: Migrasi Container lama dari `/sdcard/SecureVault/` ke internal storage berjalan pada startup gate setelah Master Password berhasil dibuka. Migrasi memverifikasi ukuran dan SHA-256, memperbarui database setelah target terverifikasi, lalu menghapus source lama. Container yang sudah berada di internal storage dilewati.
- 6.7-d5.7: Smoke test Create, Mount, Unmount, Rename, Delete, dan File Manager berhasil pada Free dan Pro setelah migrasi.
- 6.7-d5.8: `testFreeDebugUnitTest`, `testProDebugUnitTest`, `assembleFreeDebug`, dan `assembleProDebug` berhasil. Task unit test berstatus `NO-SOURCE`, sehingga belum ada unit test otomatis yang dieksekusi.
- Pengujian Auto-Lock terisolasi berhasil pada Free dan Pro: satu Container aktif berhasil dipacking, diverifikasi, plaintext-nya dihapus, dan status database berubah menjadi locked. Pada pengujian 1 menit, proses packing menambah waktu penyelesaian sekitar 22–23 detik; durasi penguncian total tidak selalu tepat pada detik ke-60.
- Startup Recovery juga berhasil diuji melalui Force Stop saat Container mounted, kemudian membuka aplikasi kembali dan menyelesaikan recovery menggunakan password Container.
- Force Stop tidak menjamin callback lifecycle berjalan. Startup Recovery merupakan mekanisme pemulihan ketika proses sebelumnya berakhir tanpa sempat melakukan Auto-Lock.
- Diagnostic logging yang digunakan selama pengujian telah dihapus dari source produksi. Build Free dan Pro setelah pembersihan logging berhasil.
- 6.7-d5 telah selesai dan diterima sebagai baseline. Implementasi Auto-Lock tidak termasuk perubahan A5.2 ini.

### 6.2 Architecture Improvement

Dikerjakan setelah Phase 6.7 selesai dan sebelum 6.8A.

Lakukan Architecture Audit terlebih dahulu dalam mode read-only. Jangan langsung mengubah kode sebelum hasil audit dibahas dan disetujui.

Area audit:

1. App Entry & Authentication — `MainActivity`, startup, navigation, authentication, Auto-Lock, dan lifecycle.
2. File Manager — listing, navigation, `RootOperations`, file operations, sorting, conflict resolution, dan selection.
3. Container — UI, authentication orchestration, Mount/Unmount, state, dan operations.
4. Settings — preferences, persistence, permission handling, dan configuration.
5. Vault — Screen, ViewModel, Repository, import, encrypt, decrypt, dan restore.
6. Shared UI — dialogs, bottom sheets, toolbar, dan reusable components.

Setelah audit, lakukan refactor kecil secara bertahap berdasarkan temuan dan persetujuan user.

Prinsip pelaksanaan:
- Thin Screen
- Single Responsibility
- State Ownership
- Separation of Concerns
- Repository untuk persistence dan data access
- Operations/Use Cases untuk business rules
- Security Boundary
- Feature Isolation
- Reusable Components
- Behavior Preservation

Hindari Clean Architecture yang berlebihan, Hilt, atau abstraksi yang tidak memberikan manfaat nyata. Pertahankan File Access Architecture 6.6D.

### 6.3 Phase 6.8 — File Manager Upgrade

Dikerjakan setelah Phase 6.7 dan Architecture Improvement selesai.

| Subtask | Deskripsi | Status |
|---|---|---|
| 6.8A | Multi-select | NOT STARTED |
| 6.8B | Batch Operations | NOT STARTED |
| 6.8C | Search | NOT STARTED |
| 6.8D | Bookmarks | NOT STARTED |

Setiap subtask harus diselesaikan, diuji, didokumentasikan, dan memiliki checkpoint sebelum melanjutkan ke subtask berikutnya.

### 6.4 Future Roadmap

| Phase | Deskripsi | Status |
|---|---|---|
| 6.9 | In-app Viewer: image dengan Coil, video dengan Media3, native PDF, dan custom text viewer | PLANNED |
| 6.10 | Localization | PLANNED |
| 6.11 | Network Clients | DEFERRED |
| 7.0 | Release Preparation: R8, signing, dan Play Store preparation | PLANNED |

### 6.5 Development & Verification Rules

- HANDOFF.md adalah sumber kebenaran utama untuk roadmap, status pekerjaan, dan prosedur handoff.
- Setiap sesi baru wajib membaca HANDOFF.md sampai selesai dan memeriksa kondisi Git sebelum bekerja.
- Jangan menganggap roadmap selesai hanya berdasarkan percakapan atau klaim sebelumnya. Verifikasi melalui repository, commit, tag, dan hasil pengujian.
- Setiap subtask harus memiliki scope yang jelas dan dikerjakan secara terpisah.
- Lakukan Architecture Check dan Behavior Preservation pada setiap subtask.
- Build sukses saja tidak cukup. Perilaku aplikasi harus diverifikasi melalui smoke test yang relevan.
- Jangan melakukan refactor di luar scope tanpa persetujuan.
- Kerja dilakukan secara lokal di Code On The Go (CoGo). Commit dan push hanya setelah satu subtask selesai atau sesuai permintaan user.
- Dokumentasikan hasil, status, pengujian, dan pekerjaan pending di HANDOFF.md.
- Jangan mengubah atau menghapus checkpoint Git yang sudah dibuat.
## 7. Diskusi vs Eksekusi
Diskusi dulu: fitur baru tanpa spec, refactor >5 file, keputusan strategis, perubahan bisa rusak tag
Langsung eksekusi: bug fix spesifik, refactor 1-3 file, sed/awk mechanical, lanjutan scope yang udah ACC
Butuh data: minta cat file.kt atau grep -rn, JANGAN nebak

## 8. Handoff Checklist (untuk AI baru)
1. Konfirmasi sudah baca + ringkas 3 poin (environment, cara kerja, workflow)
2. Minta user jalanin:
   git log --oneline -5
   git tag -l "v1.*" | sort -V | tail -10
   git status --short
3. Cek build hijau kalau perlu: ./gradlew assembleDebug 2>&1 | tail -5
4. Tanya user: lanjut dari fase mana
5. Diskusi scope singkat, baru patch
JANGAN: patch tanpa cek state, anggap konsisten, skip sanity

## 9. Pitfalls
- ./gradlew clean hapus aapt2 -> JANGAN clean, rebuild aja
- pm install dari /sdcard gagal SELinux -> pakai /data/local/tmp/
- Play tombol Code2Go stale -> pakai ./gradlew terminal
- sed/awk rusak nested try-catch -> rewrite via cat > file << EOF (heredoc)
- Copy Replace source==dest file hilang -> guard if (source == dest) di REPLACE
- Free flavor punya root code -> grep -rn 'Runtime.exec("su"' app/src/main/ harus 0 unguarded
- git add . nyedot file sampah -> cek git status --short dulu
- Warning !isRootAvailable di bash -> history expansion, bukan error
- Warning unused import Kotlin -> normal, build hijau

## 10. User Behavior
- User eksekusi sendiri di terminal, AI kasih script
- User paste output/error/smoke
- "OK"/"hijau"/"smoke OK" -> lanjut
- "Error"/"gagal"/"crash" -> minta detail, JANGAN nebak
- "Stop"/"diskusi dulu" -> berhenti patch, diskusi

## 11. AI Non-Claude (GPT/Gemini/Qwen)
Instruksi AI-agnostic. Format: terminal block bash, bukan JSON. Behavior: langsung no fluff. Verify: sanity check. Backup: .bak_<fase>. Rollback: mention tag.
Kalau tools beda, adaptasi, tapi tetap: user eksekusi terminal, AI kasih script + penjelasan.

---
Versi: 1.6 - 2026-11-21 (Phase 6.7-d5 Auto-Lock Lifecycle, Recovery, dan Internal Storage Migration selesai)

---

# 12. SecureVault v1.6.8 — Architecture Refactor

<!-- SECUREVAULT_HANDOFF_UPDATE_V1_6_8 -->

Bagian ini merupakan pembaruan resmi Handoff. Jika terdapat perbedaan dengan roadmap atau aturan lama pada bagian sebelumnya, ketentuan di bagian ini yang berlaku.

## 12.1 Baseline dan Target

- Baseline: `v1.6.7-d5`
- Baseline commit: `24958362f8a9a05c41be68f43c2140d63260a012`
- Repository: `https://github.com/savie/SecureVault.git`
- Target rilis: `v1.6.8`
- Architecture Audit telah selesai untuk scope yang diperiksa.
- Audit dilakukan secara read-only terhadap baseline repository.
- Tidak ada perubahan source code akibat audit.
- Kondisi lokal sebelumnya telah sesuai dengan baseline repository. Perbedaan yang diketahui adalah file sampah lokal yang belum di-commit dan memang harus dipertahankan.
- Tidak perlu mengulang Architecture Audit dari awal di lokal.

## 12.2 Status Pekerjaan

| Pekerjaan | Status |
|---|---|
| Architecture Audit | DONE |
| A1 — File Operation Integrity | DONE |
| A2 — Vault Operation Consolidation | DONE — checkpoint `51048bb` |
| A3 — Container Session & Security | DONE — A3.1 dan A3.2 selesai; checkpoint `8770a76` sudah di-push |
| A4 — App Entry & Recovery | DONE — implementasi dan seluruh verifikasi selesai |
| A5 — Settings Ownership | DONE — Auto-Lock dan Biometric Ownership terverifikasi |
| A6 — Shared UI Consolidation | NOT STARTED |
| Release v1.6.8 | NOT STARTED |

Audit selesai bukan berarti implementasi refactor sudah selesai.

## 12.3 Hasil Audit dan Keputusan Arsitektur

### A1 — File Operation Integrity (P1)

Temuan:
- `FileOperationHelper` menangani copy, move, delete, rename, dan conflict resolution.
- `FileAccessProvider` juga menyediakan kemampuan operasi filesystem.
- `FileOperationHelper` belum konsisten menggunakan provider.
- REPLACE berisiko menghapus tujuan sebelum hasil pengganti terverifikasi.
- Fallback MOVE berisiko menghapus sumber sebelum tujuan terverifikasi.
- Verifikasi hasil copy rekursif perlu diperkuat.

Keputusan:
- `FileAccessProvider` menjadi abstraksi kemampuan filesystem.
- `RootOperations` tetap menjadi jalur privileged untuk Pro.
- `FileOperationHelper` tetap menjadi pemilik kebijakan operasi tingkat aplikasi.
- `StorageHelper` tetap menangani storage, export, restore, dan MediaStore.
- Jangan membuat helper copy/move baru yang menduplikasi fungsi yang sudah ada.

### A2 — Vault Operation Consolidation (P1)

Temuan:
- `VaultRepository` dan `VaultImporter` memiliki jalur encrypt/decrypt yang tumpang tindih.
- File Manager menggunakan sumber File/path.
- Vault Screen menggunakan sumber Uri.

Keputusan:
- `VaultRepository` menjadi pintu utama operasi Vault.
- Kedua jenis sumber harus tetap didukung.
- `CryptoManager` tetap menjadi pemilik primitive cryptography.
- `VaultDbHelper` tetap menjadi pemilik akses metadata.
- `StorageHelper` tetap menangani strategi tujuan restore.
- Format `.svlt` tidak boleh berubah.
- Restore harus memverifikasi output sebelum menghapus encrypted source dan metadata.

### A3 — Container Session & Security (P1)

Temuan:
- `ContainerSessionManager` dan `ContainerScreen` memiliki cache password session yang tumpang tindih.
- `ContainerSessionManager` menggunakan `mutableMapOf` yang perlu ditinjau untuk concurrency.
- `ContainerRepository` sudah memiliki Mutex per Container.
- Sebagian orchestration biometric dan lock/unlock berada di `ContainerActionHandler`.

Keputusan:
- Satu pemilik session Container.
- Satu jalur operasi mount/unmount melalui ViewModel dan Repository.
- Biometric tetap menjadi metode autentikasi, bukan implementasi mount kedua.
- Manual lock, auto-lock, dan recovery memiliki trigger berbeda, tetapi menggunakan operasi repository yang konsisten.
- Pertahankan Mutex dan behavior D5.
- Jangan mengubah recovery D5 tanpa bug yang terbukti.

### A4 — App Entry & Recovery (P2)

Temuan:
- `VaultApp()` menggabungkan startup, authentication state, recovery state, dan routing.
- `MasterPasswordStore` dan `BiometricUnlockStore` sudah tersedia.

Keputusan:
- Pisahkan startup/authentication orchestration dari Composable.
- Pertahankan kedua store yang sudah ada.
- Jangan membuat sistem authentication baru.

### A5 — Settings Ownership (P2)

Temuan:
- `SettingsScreen` dan `ContainerAutoLockCoordinator` membaca konfigurasi `container_auto_lock_ms` secara terpisah.

Keputusan:
- Satu sumber konfigurasi digunakan oleh Settings dan Auto-Lock Coordinator.
- Pertahankan preference key dan behavior yang sudah ada.
- Biometric enrollment tetap menggunakan `BiometricUnlockStore`.

### A6 — Shared UI Consolidation (P2)

Temuan:
- Password input dan visibility behavior berulang pada SetupScreen, UnlockScreen, CreateContainerDialog, PasswordInputDialog, dan ContainerRecoveryScreen.
- Validasi password memiliki kebutuhan berbeda antarfitur.

Keputusan:
- Konsolidasikan visual password field dan visibility behavior yang benar-benar sama.
- Validasi password tetap mengikuti aturan setiap fitur.
- Jangan membuat satu dialog universal untuk semua kebutuhan.
- Gunakan kembali komponen UI yang sudah tersedia.

## 12.4 Target Kepemilikan Domain

| Domain | Pemilik utama |
|---|---|
| Authentication state | Satu pemilik startup/authentication state |
| Master Password storage | `MasterPasswordStore` |
| Biometric authentication | `BiometricUnlockStore` |
| File operation policy | `FileOperationHelper` |
| Filesystem capability | `FileAccessProvider` |
| Privileged filesystem | `RootOperations` |
| Vault operations | `VaultRepository` |
| Cryptography | `CryptoManager` |
| Vault metadata | `VaultDbHelper` |
| Container operations | `ContainerRepository` |
| Container session | `ContainerSessionManager` |
| Storage destination | `StorageHelper` |
| Settings configuration | Satu sumber konfigurasi |
| Shared password UI | Komponen password bersama |
| Navigation | App navigation |

ViewModel menjadi penghubung state dan operasi untuk UI. Screen berfokus pada tampilan dan interaksi UI.

## 12.5 Urutan Implementasi

1. A1 — File Operation Integrity.
2. A2 — Vault Operation Consolidation.
3. A3 — Container Session & Security.
4. A4 — App Entry & Recovery.
5. A5 — Settings Ownership.
6. A6 — Shared UI Consolidation.

Setiap tahap harus selesai dan terverifikasi sebelum melanjutkan ke tahap berikutnya.

## 12.6 Aturan Verifikasi Setiap Tahap

- Tidak ada implementasi duplikat yang tersisa.
- Tidak ada pemanggil lama yang tertinggal.
- Tidak ada perubahan format `.svlt` atau `.svct`.
- Tidak ada perubahan behavior Free/Pro yang tidak disengaja.
- Build Free dan Pro berhasil.
- Pengujian operasi terkait berhasil.
- Git diff hanya berisi perubahan subtask yang sedang dikerjakan.
- Hasil verifikasi dicatat dalam Handoff.
- D5 tidak diubah kecuali ditemukan bug yang terbukti.

### 12.6.1 Implementasi dan Verifikasi A1 — File Operation Integrity

- Status: DONE.
- Checkpoint commit: `77b24b6`.
- FileManager menggunakan pemisahan filesystem capability, privileged RootOperations, dan kebijakan operasi tingkat aplikasi.
- Perbaikan integritas copy, move, delete, rename, dan conflict resolution telah diterapkan.
- Build dan smoke test Free serta Pro telah berhasil dikonfirmasi user.

### 12.6.2 Implementasi dan Verifikasi A2 — Vault Operation Consolidation

- Status: DONE — build, smoke test Free dan Pro, serta checkpoint Git `51048bb` telah selesai.
- `VaultRepository` menjadi jalur bersama operasi Encrypt dan Decrypt untuk sumber `Uri` maupun `File/path`.
- Vault Screen dan File Manager menggunakan format `.svlt` yang sama serta penyimpanan metadata yang konsisten.
- Metadata mencakup original name, MIME type, ukuran, original path, dan original timestamp.
- Managed `.svlt` menggunakan metadata UUID untuk mengembalikan file ke original path dan timestamp.
- File `.svlt` eksternal tanpa metadata menggunakan folder pilihan sebagai fallback.
- Restore menggunakan temporary output, verifikasi hasil, dan strategi penyimpanan melalui `StorageHelper`.
- Encrypted source baru dihapus setelah hasil restore berhasil diverifikasi. Metadata Vault dibersihkan setelah source internal berhasil dihapus.
- `VaultDbHelper.deleteFile()` memverifikasi jumlah record yang terhapus dan mengembalikan Boolean.
- `VaultDecryptResult` membedakan keberhasilan restore, kegagalan penghapusan encrypted source, kegagalan penghapusan metadata, dan kegagalan restore.
- Lifecycle InputStream pada proses enkripsi diperkuat agar selalu ditutup.
- Rollback restore pada jalur Java dan Root diperkuat dengan verifikasi konten dan pemulihan backup.
- Metadata managed `.svlt` yang dipindahkan ke shared storage dibersihkan setelah restore berhasil dan source internal tidak lagi tersedia.
- Validasi ekstensi `.svlt` konsisten terhadap huruf besar/kecil.
- Verifikasi konten SHA-256 menggunakan `contentEquals()` dan wajib dipertahankan.
- Legacy `VaultImporter.kt` dihapus setelah audit tidak menemukan caller aktif. `VaultImporterHelper.kt` tetap dipertahankan karena digunakan oleh `ContainerActionHandler`.
- Variabel `destination` yang tidak digunakan pada `VaultRepository` dihapus.
- Copy/Move pada folder sumber dan tujuan yang sama menampilkan pilihan Replace, Skip, dan Keep Both.
- Replace pada source dan destination yang sama menjadi no-op agar file sumber tidak terhapus.
- Perilaku konflik untuk folder berbeda tetap dipertahankan.
- Build `assembleFreeDebug` dan `assembleProDebug` berhasil setelah seluruh perubahan dan cleanup.
- Smoke test Free dan Pro: Encrypt, Decrypt, penghapusan item Vault, serta Copy/Move pada folder yang sama telah dikonfirmasi berhasil.
- Smoke test Pro: pemindahan `.svlt` ke shared storage menggunakan Move, Decrypt dari shared storage, penghapusan source, dan pembersihan metadata Vault berhasil.
- Copy/Move pada folder yang sama telah diuji untuk Replace, Skip, dan Keep Both; seluruhnya berhasil.
- `git diff --check` bersih.
- Peringatan Kotlin unused variable yang ditargetkan telah dibersihkan. Peringatan deprecated Gradle features dan konfigurasi experimental AAPT2 masih ada dan berada di luar scope A2.
- Format `.svlt`, `CryptoManager`, dan skema database Vault tidak diubah.
- Checkpoint A2 telah dibuat pada commit `51048bb2212cde7cd22d1b4b6bada5d37848e02d` di branch `master` dan telah dipush ke GitHub.

## 12.7 Aturan Lokal CoGo dan Git

ATURAN INI WAJIB DIPATUHI:

- Pengerjaan dilakukan secara lokal di Code On The Go (CoGo).
- Repository GitHub menjadi baseline dan tidak disentuh selama implementasi lokal.
- Backup lokal dipertahankan selama masih diperlukan untuk rollback.
- Pembersihan backup dan file sementara dapat dilakukan atas instruksi eksplisit user setelah klasifikasi.
- File sampah lokal tidak boleh masuk staging maupun commit.
- Jangan menggunakan `git add .` atau `git add -A`.
- Stage hanya file yang memang termasuk pekerjaan subtask.
- Sebelum commit, periksa `git status --short` dan `git diff --cached`.
- Jangan mengulang audit arsitektur yang sudah selesai. Pemeriksaan Git hanya untuk menjaga baseline dan melindungi file lokal.
- Tidak ada commit, tag, atau push tanpa persetujuan user.

## 12.8 Release Policy

- Target akhir adalah satu rilis `v1.6.8`.
- A1 sampai A6 merupakan satu rangkaian Architecture Refactor.
- Commit checkpoint per subtask boleh dilakukan setelah pekerjaan selesai, terverifikasi, dan disetujui user.
- Tidak perlu membuat tag rilis untuk masing-masing A1 sampai A6.
- Tag `v1.6.8` hanya dibuat setelah seluruh tahap selesai dan verifikasi lulus.
- Tidak boleh menyatakan v1.6.8 selesai sebelum seluruh pekerjaan dan pengujian dikonfirmasi user.

## 12.9 Koreksi Status Baseline D5

Ketentuan ini menggantikan catatan lama yang menyebut D5 masih menunggu pemeriksaan akhir.

- Baseline pekerjaan v1.6.8 adalah `v1.6.7-d5`, commit `24958362f8a9a05c41be68f43c2140d63260a012`.
- D5 diperlakukan sebagai baseline selesai.
- Jangan mengutak-atik implementasi D5 tanpa bug yang terbukti dan persetujuan user.

## 12.10 Handoff untuk Sesi Berikutnya

AI baru wajib:
1. Membaca seluruh HANDOFF.md.
2. Mengonfirmasi environment CoGo, workflow patch/verifikasi, dan roadmap v1.6.8.
3. Memahami bahwa Architecture Audit sudah selesai dan tidak perlu diulang.
4. Memeriksa backup lokal dan hanya membersihkan file yang telah disetujui user.
5. Memeriksa Git status tanpa membersihkan file lokal.
6. Memahami A3.2 dan A4 sebagai pekerjaan selesai. Jangan menghapus file lokal yang tidak terkait dengan pekerjaan aktif.
7. Mempertahankan A4 sebagai DONE; seluruh implementasi dan verifikasi telah selesai.
8. A5 telah selesai dan diverifikasi. Tahap berikutnya adalah A6 — Shared UI Consolidation, setelah scope disetujui user.
9. Tidak langsung patch sebelum memastikan scope dan kondisi kerja.

Versi Handoff: 1.6.8 — 2026-11-23
Status: Architecture Audit DONE, A1 DONE, A2 DONE, A3 DONE, A4 DONE, A5 DONE — Auto-Lock dan Biometric Ownership selesai serta diverifikasi. A6 NOT STARTED.
Target rilis: v1.6.8.

## 12.11 Checkpoint A2 dan Hasil Final Audit

- A2 telah selesai diimplementasikan dan diverifikasi.
- Seluruh temuan final audit A2 telah ditangani.
- Build Free dan Pro berhasil setelah perubahan terakhir.
- Smoke test Free dan Pro telah dikonfirmasi user.
- Copy/Move same-folder conflict resolution telah diperbaiki dan diuji untuk Replace, Skip, dan Keep Both.
- Legacy `VaultImporter.kt` telah dihapus; `VaultImporterHelper.kt` tetap dipertahankan.
- Enam file source termasuk dalam perubahan A2: `VaultDbHelper.kt`, `VaultRepository.kt`, `FileManagerViewModel.kt`, `FileOperationHelper.kt`, `StorageHelper.kt`, serta penghapusan `VaultImporter.kt`.
- Checkpoint A2 telah dibuat pada commit `51048bb2212cde7cd22d1b4b6bada5d37848e02d` di branch `master` dan telah dipush ke GitHub.
- File backup, temporary, dan sampah lokal dipertahankan serta dikecualikan dari staging A2.

## 12.12 Implementasi dan Verifikasi A3 — Container Session & Security

### A3.1 — Session Ownership dan Auto-Lock Recovery

- Status: DONE.
- Checkpoint pada commit `77d4684bf2cd37dcf74904822b7a8f0b73bec411` di branch `master`.
- `ContainerSessionManager` menggunakan `ConcurrentHashMap` untuk menyimpan password session secara thread-safe.
- Registrasi session dilakukan oleh `ContainerRepository` setelah mount berhasil; ViewModel tidak lagi mendaftarkan session secara terpisah.
- Cache password lokal pada `ContainerScreen` dihapus.
- Manual unmount menggunakan session yang dikelola `ContainerSessionManager`.
- Transaksi unmount, recovery, dan Auto-Lock mempertahankan urutan: packing dan verifikasi arsip, pembaruan status database menjadi locked, lalu penghapusan plaintext.
- Startup Recovery memverifikasi arsip sebelum membersihkan plaintext yang tertinggal.

### A3.2 — Session Secret Boundary & Lock Orchestration

- Status: DONE — checkpoint Git `8770a765748cc9b5c2c88fda254774e27d4244e8` telah dibuat di branch `master` dan dipush ke GitHub.
- `ContainerSessionManager` menyediakan `hasSession()` dan `hasAnySession()` agar pemanggil UI dan Coordinator tidak perlu mengambil seluruh map session.
- `getAllSessions()` dihapus. API session yang mengubah atau membaca secret dibatasi ke visibilitas `internal`.
- `ContainerAutoLockCoordinator` menggunakan `hasAnySession()` untuk memeriksa keberadaan session.
- `ContainerRepository` menyediakan `unmountContainerUsingSession()` dan membaca password session setelah memperoleh Mutex container.
- Kedua jalur unmount menggunakan implementasi transaksi bersama sehingga validasi password, packing, verifikasi, pembaruan database, dan cleanup tetap konsisten.
- `ContainerViewModel` menyediakan entry point session-based unmount tanpa menerima password.
- `ContainerActionHandler` tidak lagi membaca password session. UI hanya memeriksa keberadaan session.
- Password prompt dan master-password fallback tetap menggunakan jalur unmount berbasis password.
- Biometric unlock, tombol Use Password, dan perilaku fallback tidak diubah.
- Format `.svct`, algoritma enkripsi, recovery, serta urutan transaksi Auto-Lock tidak diubah.
- Backup lima file sebelum patch tersedia dengan suffix `.pre_a3_2_20261123_105258.bak`.
- `git diff --check` bersih. Perubahan source terbatas pada lima file: `ContainerRepository.kt`, `ContainerActionHandler.kt`, `ContainerViewModel.kt`, `ContainerAutoLockCoordinator.kt`, dan `ContainerSessionManager.kt`.
- Build `assembleFreeDebug` dan `assembleProDebug` berhasil.
- APK Free (`com.securevault`, versionCode 167, versionName `1.6.7-free`) dan Pro (`com.securevault.pro`, versionCode 167, versionName `1.6.7-pro`) berhasil diinstal ulang melalui root installer CoGo.
- Runtime test Free: Password unlock, session-based manual lock, master-password lock, biometric unlock, password fallback, Auto-Lock, dan data integrity — seluruhnya lulus.
- Runtime test Pro: Password unlock, session-based manual lock, master-password lock, biometric unlock, password fallback, Auto-Lock, dan data integrity — seluruhnya lulus.
- Checkpoint A3.2 telah dibuat pada commit `8770a765748cc9b5c2c88fda254774e27d4244e8` di branch `master` dan telah dipush ke GitHub.
- Jangan stage file backup atau file lokal lain.
- Worktree bersih pada checkpoint A3.2 sebelum implementasi A4; backup dan file lokal tetap dipertahankan.

Implementasi dan verifikasi A4 selesai; hasil lengkap tercatat pada bagian 12.13.


## 12.13 Implementasi dan Verifikasi A4 — App Entry & Recovery

### Status dan Scope

- Status: DONE — implementasi dan seluruh verifikasi akhir telah dikonfirmasi user.
- Tujuan A4 adalah memisahkan startup dan authentication orchestration dari Composable tanpa membuat sistem authentication baru.
- `AppEntryViewModel` menjadi pemilik state dan orchestration untuk startup, authentication, Master Password, biometric setting, serta Container Recovery.
- Perilaku authentication, recovery, dan security yang sudah ada harus dipertahankan.

### Implementasi

- `MainActivity.kt` menggunakan `AppEntryViewModel` untuk mengelola startup dan authentication flow.
- `AppEntryViewModel.kt` menjadi pemilik loading Master Password, authentication stage, biometric setting, recovery migration, pemeriksaan recovery, retry, dan manual recovery.
- `ContainerRecoveryScreen.kt` difokuskan sebagai UI untuk menampilkan dan menjalankan interaksi recovery.
- `SetupScreen.kt` menggunakan asynchronous save state untuk penyimpanan Master Password.
- Activity recreation dengan `savedInstanceState != null` mengembalikan aplikasi ke tahap Unlock sehingga autentikasi diminta kembali.
- Cancellation coroutine, auth generation guard, serta pembatalan dan tracking recovery job ditangani untuk mencegah pekerjaan lama memperbarui state yang sudah tidak berlaku.
- Error pada proses loading dan penyimpanan Master Password ditangani tanpa membiarkan state busy menggantung.
- `MasterPasswordStore`, `BiometricUnlockStore`, dan `ContainerRepository` tidak diubah.
- Model authentication, format file, transaksi repository A3, dan mekanisme Auto-Lock tidak diubah.

### File yang Berubah

- `app/src/main/java/com/securevault/MainActivity.kt`
- `app/src/main/java/com/securevault/ui/screens/SetupScreen.kt`
- `app/src/main/java/com/securevault/ui/screens/ContainerRecoveryScreen.kt`
- `app/src/main/java/com/securevault/ui/viewmodel/AppEntryViewModel.kt` (file baru)

### Build dan Runtime Verification

- `assembleFreeDebug` dan `assembleProDebug` berhasil.
- APK Free (`com.securevault`) dan Pro (`com.securevault.pro`) berhasil diinstal melalui root installer CoGo.
- Startup dan Master Password Unlock: lulus pada Free dan Pro.
- Container Recovery startup gate: lulus.
- Activity recreation melalui rotasi dan permintaan autentikasi ulang: lulus.
- Global Lock: lulus.
- Master Password recovery dan Custom Password recovery: lulus.
- Wrong password dan retry: lulus.
- Vault Encrypt, Decrypt, Rename, dan Delete: lulus.
- Container create, Rename, Unlock/Mount, File Manager operation, Lock/Unmount, Auto-Lock, dan re-entry: lulus.
- Pengujian Container mencakup Master Password maupun Custom Password, dengan dan tanpa biometric.
- Biometric unlock dan fallback tetap berfungsi.

### Verifikasi Akhir — Seluruhnya Lulus

1. Lock ketika recovery coroutine sedang aktif: LULUS. Cancellation dan state guard telah diverifikasi.
2. Asynchronous Master Password save pada SetupScreen melalui fresh install tanpa Master Password: LULUS.

- Seluruh implementasi dan verifikasi A4 telah selesai dan dikonfirmasi user.
- Belum ada checkpoint Git A4.
- Backup historis A4 telah dibersihkan setelah pekerjaan A5 diverifikasi. Backup rollback A5 dipertahankan sampai checkpoint berhasil di-push.

Tahap berikutnya: A6 — Shared UI Consolidation, setelah scope dan kondisi repository diverifikasi.

## 12.14 Implementasi dan Verifikasi A5 — Settings Ownership

### A5.1 — Auto-Lock Settings Ownership

- Status: DONE.
- Baseline: `e01b600` — `refactor: centralize auto-lock settings and diagnostics`.
- `AutoLockSettingsStore` menjadi pemilik konfigurasi `container_auto_lock_ms`, termasuk default, validasi, pembacaan, dan penyimpanan.
- `SettingsScreen` dan `ContainerAutoLockCoordinator` menggunakan Store yang sama.
- `AutoLockDiagnosticsStore` menyediakan bounded diagnostic history melalui Settings.
- Pengujian Auto-Lock Free dan Pro telah berhasil dikonfirmasi user.
- Auto-Lock dianggap stabil. Jangan mengubah implementasinya tanpa bug yang terbukti dan persetujuan user.

### A5.2 — Biometric Ownership

- Status: DONE — implementasi dan runtime verification dikonfirmasi user.
- `BiometricUnlockStore` menjadi satu-satunya pemilik persistence biometric global dan enrollment material.
- `AppEntryViewModel` tidak lagi mengakses SharedPreferences biometric secara langsung.
- Startup membaca status enrollment melalui `BiometricUnlockStore.isEnrolled()`.
- `setBiometricEnabled()` hanya memperbarui UI state.
- `BiometricUnlockStore.disable()` mengembalikan hasil commit persistence.
- Enrollment memeriksa keberhasilan pembersihan enrollment lama sebelum membuat enrollment baru.
- Settings hanya memperbarui state setelah operasi Store berhasil.
- Preference keys, token/hash format, dedicated Keystore alias, AES/GCM, dan authentication flow dipertahankan.
- Biometric Master Password dan Container berhasil diuji user.

### UI Regression Fix

- Status: DONE — build dan runtime verification Free serta Pro berhasil.
- `SettingsScreen` menggunakan `verticalScroll` agar seluruh pengaturan dapat diakses.
- Bottom Navigation otomatis tersembunyi saat scroll ke bawah dan muncul kembali saat scroll ke atas.
- Bottom Navigation kembali terlihat ketika destination berubah.
- Pengujian scrolling dan auto-hide/auto-show dikonfirmasi berhasil user.

### Final Build dan Runtime Verification

- `assembleFreeDebug` dan `assembleProDebug` berhasil pada 24 November 2026.
- APK Free `com.securevault`, versi `1.6.7-free`, versionCode 167 berhasil diinstal.
- APK Pro `com.securevault.pro`, versi `1.6.7-pro`, versionCode 167 berhasil diinstal.
- Biometric Master Password dan Container: PASS.
- Settings scroll sampai bagian bawah: PASS.
- Bottom Navigation auto-hide dan auto-show: PASS.
- Reset Bottom Navigation saat pergantian tab: PASS.
- `git diff --check`: PASS.
- Final audit sebelum update Handoff: hanya empat source file implementasi berubah.
- Tidak ada perubahan pada `AutoLockSettingsStore`, `ContainerAutoLockCoordinator`, `ContainerRepository`, `ContainerEngine`, atau `AutoLockDiagnosticsStore`.
- Tidak ada perubahan format `.svct`, `.svlt`, atau transaksi Auto-Lock.
- A5 siap menjadi satu checkpoint Git bersama pembaruan Handoff.

### File Checkpoint A5

1. `app/src/main/java/com/securevault/MainActivity.kt`
2. `app/src/main/java/com/securevault/ui/screens/SettingsScreen.kt`
3. `app/src/main/java/com/securevault/ui/viewmodel/AppEntryViewModel.kt`
4. `app/src/main/java/com/securevault/utils/BiometricUnlockStore.kt`
5. `HANDOFF.md`

Tahap berikutnya: A6 — Shared UI Consolidation. Lakukan scope discussion dan audit sesuai roadmap sebelum implementasi.
