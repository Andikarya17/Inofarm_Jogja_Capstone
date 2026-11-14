# Bukti fase D3: hardening backend

Tanggal 14 November 2026. Branch `codex/deploy-backend-hardening`, bertumpuk di atas `codex/deploy-dependency-patch`. Rujukan: [audit deployment](DEPLOYMENT_READINESS_AUDIT_20261114.md) temuan P3-1, P0-5, P2-2; [bukti D2](DEPLOYMENT_DEPENDENCY_EVIDENCE_20261114.md).

## P3-1: role diperiksa sebelum validasi (diuji)

Satu middleware rute, `finance-manager` (`backend/app/Http/Middleware/FinanceManager.php`), dipasang pada 31 definisi rute tulis khusus pengelola di `backend/routes/api_v2.php`. Middleware berjalan setelah `auth:sanctum` dan `finance-session`, sebelum controller memvalidasi payload. Pesannya sama dengan penolakan yang sudah ada: "Akses pengelola diperlukan." Pemeriksaan role di dalam layanan domain tetap ada; middleware ini hanya gerbang awal.

Rute yang dipakai pekerja tidak diubah: `auth/logout`, `sync/operations`, `sync/reconcile`, `attachments` (unggah), serta buat, koreksi dan batal pada `activities` dan `harvests`. Urutan definisi rute juga tidak diubah.

`backend/tests/Feature/ManagerOnlyWriteGateTest.php`, 3 tes dan 118 assertion di `inofarm_test`:

| Tes | Yang dibuktikan |
|---|---|
| Pekerja + payload kosong | 56 permintaan tulis menghasilkan 403 tanpa `field_errors`: buat dan ubah untuk 18 resource (master, jadwal, target, biaya, alokasi, aset, penyusutan, penjualan, skenario investasi, akun), 7 pembatalan (termasuk lampiran), tutup dan buka musim, tanam saat ini, selesai panen, cek kegiatan, cek panen, kandidat penugasan, preview dan terbit laporan musim dan investasi, serta backup dan validasi recovery |
| Pengelola + payload kosong | Tetap sampai ke validasi (422) pada 5 rute contoh, jadi gerbang tidak menghalangi pengelola |
| Inventaris rute | Setiap rute POST/PATCH/PUT/DELETE di `api/v2` harus punya gerbang atau termasuk 11 rute tulis pekerja yang didaftar di tes. Rute baru tanpa keputusan membuat tes ini gagal |

Kontrak sukses tidak berubah; pemeriksa kontrak lulus. Yang berubah hanya respons untuk pekerja dengan payload tidak valid pada rute khusus pengelola: dari 422 menjadi 403.

**Tidak dijalankan ulang:** probe 23 GET dari audit. Rute GET tidak disentuh fase ini.

## P0-5: jadwal dan backup

**Pembersihan token (diuji lewat `schedule:list`):** `routes/console.php` menjadwalkan `sanctum:prune-expired --hours=24` setiap hari pukul 00.00. Jadwal ini baru berjalan bila cron `php artisan schedule:run` dipasang di server (runbook D5). Di laptop, tidak ada cron, jadi belum berjalan otomatis.

**Skrip backup:** `scripts/backup-mysql.ps1` (laptop Windows) dan `scripts/backup-mysql.sh` (server Linux). Keduanya membaca `DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD` dari env, dan mengisi kunci yang kosong dari berkas env yang ditunjuk. Password diteruskan ke `mysqldump` lewat `MYSQL_PWD`, tidak lewat argumen proses dan tidak dicetak. Keluarannya berkas bertanggal, berkas `.sha256`, dan penghapusan cadangan yang lebih tua dari 14 hari (nilai pilot G5). Dump tanpa penanda "Dump completed" dihapus dan dianggap gagal.

| Uji pada `inofarm_test` | Hasil |
|---|---|
| `backup-mysql.ps1` | Berkas 79.679 byte; isi `.sha256` cocok dengan hash yang dicetak |
| `backup-mysql.sh` lewat Git Bash | Berkas 79.679 byte; `sha256sum -c` OK; penanda "Dump completed" ada |
| `backup-mysql.sh` tanpa env | Berhenti dengan kode 2, "DB_HOST belum diisi" |
| Dump diputar ulang ke `inofarm_test` | `mysql` exit 0; sebelum dan sesudah sama: 55 tabel, 77 FK, 25 baris migration |
| Banding dump logis sebelum dan sesudah | 1.312 baris, 0 berbeda setelah kata kunci `CHARACTER SET utf8mb4` dinormalkan |
| Suite PHPUnit setelah diputar ulang | 162 tes lulus |

Catatan banding: setelah dump diputar ulang, MySQL 8.0.30 menulis `CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci` pada kolom yang sebelumnya ditulis `COLLATE utf8mb4_unicode_ci` saja. `information_schema` menunjukkan 222 kolom teks tetap `utf8mb4` dengan `utf8mb4_unicode_ci`, sama dengan kolasi tabel. Ini beda penulisan, bukan beda schema.

**Belum diverifikasi:**

- **Restore ke database terpisah belum bisa dijalankan.** Akun MySQL aplikasi tidak punya hak `CREATE DATABASE` (galat 1044), dan sesi ini tidak memakai akun root. Yang diuji adalah memutar ulang dump ke `inofarm_test` sendiri. Untuk uji restore terpisah, pemilik perlu membuat database kosong dan memberi hak pada akun aplikasi; langkahnya masuk runbook D5.
- **Backup `inofarm_finance_local` dari D1 belum pernah dipulihkan.**
- `backup-mysql.sh` diuji dengan Git Bash dan `mysqldump.exe` di Windows, bukan di Linux. Retensi 14 hari dibaca dari kode; tidak ada berkas tua untuk membuktikan penghapusan.
- Belum ada jadwal backup yang terpasang di laptop atau server. Skrip tidak mengenkripsi berkas; lokasi tujuan harus privat.

## G4 dan G5: contoh env produksi

`backend/.env.production.example` tanpa nilai rahasia: `APP_ENV=production`, `APP_DEBUG=false`, `LOG_LEVEL=warning`, `SESSION_DRIVER=file`, catatan `TrustProxies`, dan nilai pilot:

| Kunci | Nilai | Sumber |
|---|---|---|
| `FINANCE_SESSION_SECONDS` | 604800 (7 hari) | G4 |
| `FINANCE_ATTACHMENT_MAX_BYTES` | 5242880 (5 MB) | G5, Q07 pilot |
| `FINANCE_ATTACHMENT_MAX_FILES` | 5 | G5, Q07 pilot |
| `FINANCE_ATTACHMENT_MIME_TYPES` | `image/jpeg,image/png,application/pdf` | G5, Q07 pilot |

Kode tidak diubah: bawaan sesi tetap 24 jam dan lampiran tetap mati sampai env diisi. Nilai lampiran berada di dalam batas yang didukung `AttachmentPolicy` (maksimal 32 MB, tiga jenis itu); dibaca dari kode, belum diuji dengan env terpasang. `.env` laptop tidak dibaca ke chat dan tidak diubah, jadi backend laptop masih memakai sesi 24 jam.

## Pemeriksaan fase

| Pemeriksaan | Sebelum D3 | Sesudah D3 |
|---|---|---|
| Guard database uji | 4 tes, 12 assertion | sama |
| Backend PHPUnit, hanya `inofarm_test` | 159 tes, 3.983 assertion | 162 tes, 4.101 assertion, 0 gagal |
| `check-finance-contracts.py` | lulus | lulus |
| `composer audit --locked` | 0 advisory | 0 advisory |
