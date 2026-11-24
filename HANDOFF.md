SECUREVAULT — HANDOFF

Repository: https://github.com/savie/SecureVault.git
Lokal: "~/SecureVault" — Code On The Go (CoGo)

Dokumen ini merupakan panduan operasional utama AI Assistant untuk SecureVault. Wajib dibaca sampai selesai sebelum melakukan audit, implementasi, atau perubahan repository.

1. PERAN & BAHASA

- AI berperan sebagai pair-programmer SecureVault.
- Komunikasi menggunakan bahasa Indonesia santai, langsung, dan tanpa basa-basi.
- Kode, identifier, dan UI aplikasi menggunakan bahasa Inggris.
- Dokumentasi repository menggunakan bahasa Indonesia resmi. Istilah teknis dan canonical terminology dapat dipertahankan dalam bahasa Inggris.
- Jangan mengarang kondisi repository, hasil build, maupun hasil pengujian.
- Jangan menyatakan pekerjaan selesai sebelum hasil verifikasi dikonfirmasi user.

2. ENVIRONMENT

Komponen| Konfigurasi
OS| Android
IDE| Code On The Go (CoGo)
Kotlin| 1.9+
JVM| 17
Gradle| 9.6.1
minSdk / targetSdk / compileSdk| 26 / 34 / 34
Free package| "com.securevault"
Pro package| "com.securevault.pro"

Build Free dan Pro

cd ~/SecureVault || exit 1
./gradlew assembleFreeDebug assembleProDebug --console=plain

Lokasi APK:

- Free: "app/build/outputs/apk/free/debug/app-free-debug.apk"
- Pro: "app/build/outputs/apk/pro/debug/app-pro-debug.apk"

Instalasi APK

Jangan menginstal APK langsung dari "/sdcard". Salin APK ke "/data/local/tmp/", kemudian jalankan instalasi melalui root.

Contoh:

su -c "cp app/build/outputs/apk/free/debug/app-free-debug.apk /data/local/tmp/sv.apk"
su -c "pm install -r /data/local/tmp/sv.apk"

Sesuaikan sumber APK untuk flavor yang akan diuji.

Larangan CoGo

- Jangan menjalankan "./gradlew clean".
- Jangan menghapus "~/.androidide/aapt2".
- Jangan menginstal APK langsung dari "/sdcard".
- Jangan mengandalkan tombol Play Code2Go sebagai bukti hasil build.
- Build dilakukan melalui terminal.

Jika AAPT2 rusak, pulihkan menggunakan binary ARM64 AndroidIDE:

curl -L -o "$HOME/aapt2-arm64" "https://github.com/AndroidIDEOfficial/platform-tools/releases/download/v34.0.4/aapt2-arm64-v8a"
cp "$HOME/aapt2-arm64" "$HOME/.androidide/aapt2"
chmod +x "$HOME/.androidide/aapt2"
rm -f "$HOME/aapt2-arm64"

3. CARA KERJA AI–USER

Setiap subtask mengikuti siklus operasional berikut:

1. AI membaca Roadmap dan memahami tujuan pekerjaan.
2. AI memeriksa informasi yang tersedia dan mengidentifikasi kebutuhan audit.
3. Jika scope belum jelas, lakukan diskusi dan sepakati batas pekerjaan terlebih dahulu.
4. User menyetujui scope.
5. AI menyiapkan patch dalam satu blok Bash siap copy-paste.
6. User menjalankan command melalui terminal CoGo dan mengirimkan output.
7. AI memeriksa output sebelum memberikan instruksi tahap berikutnya.
8. Setelah patch terverifikasi, AI memberikan instruksi build.
9. Setelah build berhasil, AI memberikan instruksi instalasi dan smoke test.
10. Setelah smoke test dikonfirmasi berhasil, AI menyiapkan script checkpoint.
11. Commit dan push hanya dilakukan setelah persetujuan user.
12. Setelah checkpoint, AI memperbarui Implementation Status dan menentukan pekerjaan berikutnya.

Aturan penyusunan patch

Setiap pekerjaan implementasi harus memiliki:

- Tujuan perubahan.
- Scope file yang terdampak.
- Satu terminal block siap copy-paste.
- Expected output dan sanity check.
- Next step yang jelas.

Setiap script patch harus mencakup:

1. Repository navigation.
2. Precondition check.
3. Backup file sebelum perubahan.
4. Validasi target file dan jumlah pola yang akan diubah.
5. Implementasi perubahan.
6. Sanity check.
7. Diff untuk pemeriksaan hasil.

Ketentuan tambahan:

- Hentikan script jika precondition atau jumlah pola tidak sesuai asumsi.
- Gunakan heredoc ("cat <<'EOF'") untuk full-file rewrite jika lebih aman daripada manipulasi teks.
- Gunakan "grep", "wc -l", "sha256sum", atau pemeriksaan lain jika diperlukan.
- Jangan meminta user menyusun atau menggabungkan potongan patch secara manual.
- Jangan mengirim source code untuk disalin manual jika perubahan dapat dilakukan melalui script terminal.
- Jangan melanjutkan tahap berikutnya sebelum output tahap sebelumnya diperiksa.
- Gunakan "git --no-pager diff" atau "GIT_PAGER=cat" agar output tidak masuk pager.
- Jangan menganggap patch, build, instalasi, atau smoke test berhasil tanpa output atau konfirmasi user.

Diskusi sebelum implementasi

Lakukan diskusi dan sepakati scope terlebih dahulu jika:

- Scope ambigu atau fitur belum memiliki spesifikasi.
- Refactor melibatkan lebih dari lima file.
- Ada perubahan arsitektur atau security boundary.
- Perubahan berpotensi memengaruhi behavior yang sudah stabil.
- Pekerjaan mengubah roadmap atau prioritas.

Untuk bug yang belum jelas, minta data diagnostik seperti "cat", "grep", logcat, stack trace, atau informasi relevan lainnya. Jangan menebak penyebab.

Trigger percakapan

Trigger| Tindakan
"Lanjut" / "Gas"| Lanjutkan scope yang sudah disepakati
"Build hijau"| Berikan smoke test checklist
"Smoke OK"| Siapkan script checkpoint
"Error" / "Crash"| Investigasi berdasarkan output aktual
"Stop" / "Diskusi dulu"| Hentikan patch dan lakukan diskusi

Aturan checkpoint

Sebelum commit:

- Periksa branch dan working tree.
- Periksa staged files.
- Periksa staged diff dan "git diff --cached --check".
- Pastikan hanya file terkait yang masuk staging.
- Pastikan backup dan temporary files tidak ikut di-commit.

Commit, tag, dan push harus menggunakan script yang jelas serta menunggu persetujuan user. Build hijau atau smoke test berhasil tidak memberikan izin otomatis untuk commit.

4. REPOSITORY & GIT SAFETY

Sumber kerja

- GitHub merupakan baseline untuk audit awal.
- Audit awal dilakukan langsung terhadap repository GitHub dalam mode read-only.
- Audit awal tidak boleh mengubah repository.
- Implementasi, build, dan pengujian dilakukan pada repository lokal CoGo.
- Kondisi lokal wajib diperiksa sebelum patch.
- Jangan menganggap repository lokal identik dengan GitHub.
- Jangan menyentuh GitHub selama tahap implementasi lokal. Sinkronisasi dilakukan melalui checkpoint yang disetujui user.

Pemeriksaan kondisi lokal

Jalankan:

cd ~/SecureVault || exit 1

echo "===== BRANCH ====="
git branch --show-current

echo "===== RECENT COMMITS ====="
git log --oneline -5

echo "===== RECENT TAGS ====="
git tag -l "v1.*" | sort -V | tail -10

echo "===== WORKING TREE ====="
git status --short

Perlindungan pekerjaan lokal

- Jangan menghapus file untracked atau backup tanpa klasifikasi dan persetujuan user.
- Buat backup sebelum patch.
- Pertahankan backup rollback sampai checkpoint dinyatakan aman.
- Jangan menimpa pekerjaan lokal yang berada di luar scope.
- Jika kondisi lokal berbeda dari checkpoint Handoff, gunakan kondisi aktual sebagai dasar investigasi dan diskusikan perbedaannya.
- Jangan menganggap file yang tidak dikenal sebagai file sampah.

Larangan Git

Dilarang menggunakan:

- "git clean"
- "git reset --hard"
- "git add ."
- "git add -A"
- Perintah checkout atau restore yang berpotensi menimpa pekerjaan lokal tanpa pemeriksaan dan persetujuan.

Stage hanya file yang relevan secara eksplisit.

Tidak boleh melakukan commit, tag, atau push tanpa persetujuan user.

5. ARSITEKTUR

Package utama: "com.securevault".

Area| Tanggung jawab
"MainActivity.kt"| Activity dan navigation
"data/"| Database dan repository
"fs/"| Filesystem capability
"ui/screens/"| Screen
"ui/viewmodel/"| UI state dan orchestration
"ui/components/"| Reusable UI
"utils/"| Utility dan domain support

File Access Architecture 6.6D

- "FileAccessProvider" dan "StandardFileAccessProvider" berada di main source set.
- "FileAccessManager" menjadi entry point.
- "RootOperations" merupakan interface bersama.
- "FreeRootOperations" dan "ProRootOperations" berada di source set masing-masing.
- libsu hanya menjadi dependency Pro.
- Root implementation tidak boleh bocor ke Free.

Domain ownership

Domain| Pemilik
Master Password| "MasterPasswordStore"
Biometric| "BiometricUnlockStore"
File operation policy| "FileOperationHelper"
Filesystem| "FileAccessProvider"
Root operations| "RootOperations"
Vault operations| "VaultRepository"
Cryptography| "CryptoManager"
Vault metadata| "VaultDbHelper"
Container operations| "ContainerRepository"
Container session| "ContainerSessionManager"
Storage destination| "StorageHelper"
Navigation| App navigation

Screen berfokus pada UI. ViewModel menghubungkan UI state dengan operasi domain. Hindari abstraksi, dependency, dan perpindahan ownership yang tidak diperlukan.

6. ARCHITECTURE & SECURITY GUARDRAILS

Data dan cryptography

- Pertahankan format ".svlt" dan ".svct".
- Jangan mengubah skema penyimpanan atau algoritma enkripsi tanpa audit dan persetujuan user.
- Vault restore wajib memverifikasi hasil sebelum menghapus encrypted source dan metadata.
- Pertahankan SHA-256 content verification.
- Jangan mengubah format data yang sudah digunakan tanpa evaluasi kompatibilitas dan rencana migrasi.

Container dan session

- Container mount/unmount harus menggunakan jalur repository yang konsisten.
- Pertahankan Mutex, session ownership, dan urutan transaksi Container.
- Biometric merupakan metode autentikasi, bukan implementasi mount kedua.
- Container yang menggunakan Master Password harus mempertahankan behavior yang sudah ada.
- Jangan memindahkan tanggung jawab session atau secret ke layer yang tidak semestinya.

Storage aktif

Data| Lokasi
Vault| "context.filesDir/vault/"
Container| "context.filesDir/containers/"
Mounted plaintext| "context.filesDir/containers_temp/{containerId}/"
Export/Backup| "/sdcard/SecureVault/"

"/sdcard/SecureVault/" disiapkan untuk Export/Backup, bukan penyimpanan aktif.

Auto-Lock protected area

Auto-Lock merupakan komponen terlindungi. Jangan mengubah tanpa bug yang terbukti dan persetujuan user.

File terkait:

- "AutoLockSettingsStore.kt"
- "ContainerAutoLockCoordinator.kt"
- "ContainerRepository.kt"
- "ContainerEngine.kt"
- "AutoLockDiagnosticsStore.kt"

Pertahankan:

- Packing dan verification.
- Database update.
- Plaintext cleanup.
- Recovery.
- Internal storage migration.
- Urutan operasi dan lifecycle yang sudah ada.

Free dan Pro boundary

- Jangan membocorkan root implementation ke Free.
- Jangan memasukkan dependency libsu ke Free.
- Periksa source set dan dependency setiap kali ada perubahan terkait root.
- "Runtime.exec("su")" tidak boleh masuk ke jalur Free tanpa guard dan pemisahan yang benar.

7. AUDIT & IMPLEMENTATION RULES

Audit

- Audit awal wajib dimulai dari GitHub dalam mode read-only.
- Identifikasi file, caller, dependency, ownership, dan behavior yang berkaitan dengan scope.
- Catat kondisi aktual, masalah, dampak, rekomendasi, dan risiko.
- Bedakan:
  - Confirmed Issue — masalah terbukti dari implementasi atau bukti yang tersedia.
  - Potential Issue — indikasi masalah yang masih memerlukan verifikasi.
  - Improvement Opportunity — peluang perbaikan, bukan bug.
- Jangan menyebut dugaan sebagai bug terbukti.
- Jangan mengubah repository selama audit.
- Setelah audit, diskusikan hasil dan tunggu persetujuan scope sebelum implementasi lokal.

Implementasi

Sebelum patch, periksa kondisi branch dan working tree lokal, baca file serta caller terkait, verifikasi signature dan dependency, dan pastikan perubahan sesuai scope.

Seluruh prosedur patch, backup, validasi, build, smoke test, dan checkpoint mengikuti aturan pada Bagian 3 dan 4.

Kriteria penyelesaian

Subtask baru dapat dinyatakan DONE jika:

- Implementasi selesai.
- Sanity check berhasil.
- Build Free dan Pro berhasil jika keduanya terdampak atau diwajibkan scope.
- Smoke test relevan berhasil dan dikonfirmasi user.
- Dokumentasi terkait diperbarui jika diperlukan.
- Checkpoint telah disetujui dan berhasil jika subtask memerlukan checkpoint.

Build hijau saja tidak cukup untuk menyatakan subtask selesai.

8. KNOWN PITFALLS

Masalah| Penanganan
"gradlew clean" merusak AAPT2| Jangan menjalankan clean
APK dari "/sdcard" gagal install| Gunakan "/data/local/tmp/"
Tombol Play Code2Go menunjukkan hasil stale| Build melalui terminal
Manipulasi "sed"/"awk" merusak nested Kotlin| Gunakan heredoc rewrite jika lebih aman
Copy/Replace source sama dengan destination| Tambahkan guard "source == destination"
Root code bocor ke Free| Periksa source set dan dependency
"git add ." mengambil file tidak terkait| Stage file secara eksplisit
Git diff masuk pager| Gunakan "git --no-pager diff"
Crash tanpa informasi| Minta logcat dan stack trace
Patch gagal di tengah proses| Periksa working tree dan gunakan backup rollback
File backup dianggap sampah| Klasifikasi dan minta persetujuan sebelum menghapus

9. NEW SESSION CHECKLIST

AI pada sesi baru wajib:

1. Membaca seluruh HANDOFF.
2. Memahami environment dan cara kerja AI–user.
3. Membaca Architecture & Security Guardrails.
4. Membaca Official Roadmap.
5. Membaca Implementation Status dan checkpoint terakhir.
6. Meminta output branch, "git log -5", tag, dan "git status --short".
7. Memeriksa kondisi lokal tanpa mengubahnya.
8. Membandingkan kondisi aktual dengan checkpoint Handoff.
9. Mengidentifikasi pekerjaan berikutnya berdasarkan Roadmap.
10. Mengonfirmasi kepada user sebelum memulai scope baru.

Jangan langsung melakukan patch setelah membaca Handoff.

Jika kondisi Git berbeda dari checkpoint Handoff, gunakan repository aktual sebagai sumber kebenaran untuk kondisi lokal dan diskusikan perbedaannya terlebih dahulu.

---

10. OFFICIAL ROADMAP

Roadmap adalah acuan pengembangan SecureVault. Perubahan scope dan prioritas harus disetujui user.

10.1 Architecture Improvement — Phase 6.8

Architecture Audit: DONE (read-only).

Subtask| Scope| Status
A1| File Operation Integrity| DONE
A2| Vault Operation Consolidation| DONE
A3| Container Session & Security| DONE
A4| App Entry & Recovery| DONE
A5| Settings Ownership| DONE
A6| Shared UI Consolidation| NOT STARTED

A6: Konsolidasi password field dan visibility pada "SetupScreen", "UnlockScreen", "CreateContainerDialog", "PasswordInputDialog", dan "ContainerRecoveryScreen".

Pertahankan validasi per fitur, authentication, recovery, dan Auto-Lock. Jangan membuat universal dialog. Scope final ditentukan melalui audit GitHub dan persetujuan user.

10.2 Planned Development

Phase| Scope| Status
6.9A–D| Multi-select, Batch Operations, Search, Bookmarks| PLANNED
7.0| In-app Viewer: Image, Video, PDF, Text| PLANNED
7.1| Localization (default English)| PLANNED
7.2| Network Clients| DEFERRED
8.0| Release: R8, Signing, Play Store| PLANNED

Phase 6.7 mencakup Container Database Migration, Master Password, Biometric, Vault Encryption Integrity, serta Auto-Lock Lifecycle dan Recovery. Hasilnya menjadi baseline yang wajib dipertahankan.

Phase 6.9 dikerjakan setelah Phase 6.8 selesai. Setiap subtask tetap memerlukan scope dan acceptance criteria sebelum implementasi.

Phase 7.0 menyediakan viewer internal agar file dapat dibuka tanpa selalu menggunakan aplikasi eksternal.

Phase 7.1 menambahkan dukungan multi-language tanpa mengubah business logic.

Phase 8.0 mencakup code shrinking, signing, release verification, APK/AAB validation, dan persiapan Google Play.

Roadmap merupakan backlog jangka panjang. Rencana tidak boleh dihapus atau diubah diam-diam; perubahan harus disetujui user.

---

11. IMPLEMENTATION STATUS & GIT CHECKPOINTS

11.1 Current Baseline

Item| Nilai
Branch| "master"
Last checkpoint| "0aa81b576cb0afd7180c99baadd9ace67fd4676a"
Commit| "refactor: complete settings ownership"
Target release| "v1.6.8"
Next task| A6

Verifikasi kondisi aktual melalui Git pada setiap sesi baru.

11.2 Checkpoints

Subtask| Commit
Architecture Audit| Read-only
A1| "77b24b6"
A2| "51048bb"
A3.1| "77d4684"
A3.2| "8770a76"
A4| Tidak ada checkpoint terpisah
A5| "0aa81b5"

11.3 Pending

- A6 — Shared UI Consolidation.
- Biometric Unmount untuk Container dengan manual password (terpisah dari A6).
- Release "v1.6.8" setelah A1–A6 dan seluruh verifikasi selesai.

Tidak perlu tag per subtask. Tag release hanya dibuat setelah verifikasi selesai dan mendapat persetujuan user.

11.4 Handoff Maintenance

- Bagian 1–9: aturan operasional dan arsitektur.
- Bagian 10: roadmap dan rencana pengembangan.
- Bagian 11: status aktual dan checkpoint.
- Jangan memasukkan kronologi implementasi atau laporan pengujian.

HANDOFF adalah panduan kerja, bukan changelog.

---

END OF HANDOFF
Versi: 1.7
Tanggal pembaruan: 24 November 2026
