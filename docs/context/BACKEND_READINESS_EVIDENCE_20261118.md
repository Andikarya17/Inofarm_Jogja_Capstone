# Kesiapan backend: bukti (18 November 2026)

Atas permintaan pengguna 18 November 2026 ("jalankan semua yang perlu dikerjakan"), dari `main` `5e37ca7` (setelah PR #79, #83, #84). Branch `api-guest-401`. Label bukti: **tes PHPUnit** (`inofarm_test`), **database aplikasi** (`inofarm_finance_local`, hanya baca), **instance MySQL sementara** (untuk restore). Tidak ada uji di perangkat fisik.

## Ringkasan

| Pekerjaan | Hasil |
|---|---|
| PHPUnit di `main` sesudah merge | guard 4/12, backend **174 tes / 4.751 assertion** lulus |
| Perbaikan 500 → 401 untuk tamu (usulan E3) | tes baru gagal sebelum perbaikan (500 `Route [login] not defined`), lulus sesudahnya; backend **175 / 4.761** lulus, checker kontrak lulus |
| Pembacaan `inofarm_finance_local` | inbox Cek catatan dan Beranda terbaca untuk pengelola aktif, tidak ada migration tertunda, tidak ada baris yang berubah |
| Restore backup 18 November | berhasil ke instance MySQL sementara; isi tabel yang tidak disentuh migration 22 identik; migration 22 lalu berjalan di salinan itu sampai sama dengan database aplikasi |

## 1. PHPUnit di main

`./scripts/test-backend.ps1` di `main` `5e37ca7`. Backend uji di port 8001 dimatikan dulu, sehingga tidak ada penulis lain di `inofarm_test`. Hasil: guard 4 tes / 12 assertion, backend 174 tes / 4.751 assertion. Sama dengan hasil terakhir di branch #81; PR #79, #83 dan #84 tidak mengubah kode backend.

## 2. Tamu tanpa token mendapat 401, bukan 500

Penyebab: tanpa token dan tanpa header `Accept: application/json`, middleware auth Laravel mencoba mengarahkan ke route `login`. Route itu tidak ada (tidak ada halaman web), sehingga server menjawab 500 sebelum handler JSON di `bootstrap/app.php` bekerja.

Perbaikan: `$middleware->redirectGuestsTo(fn () => null)`. Tamu tidak diarahkan ke mana pun, middleware melempar `AuthenticationException`, dan handler yang sudah ada menjawab 401 `authentication_required`.

Tes `FinanceAuthScopeTest::test_guest_without_json_accept_gets_401_not_500` memeriksa `GET me`, `GET sync/status`, `GET greenhouses`, token palsu, dan `POST sync/operations`, semuanya tanpa header JSON.

Aplikasi Android tidak terdampak, karena selalu mengirim `Accept: application/json` (`FinanceApi.kt`). Yang membaik adalah klien lain, pemindai keamanan dan log server.

## 3. Pembacaan database aplikasi

Database `inofarm_finance_local` di `127.0.0.1:3307` dibaca dengan konfigurasi backend biasa (bukan `testing`). Skrip berhenti bila database efektif bukan `inofarm_finance_local`. Layanan yang sama dengan endpoint `activity-reviews` (pending dan decided) dan `manager-home` (ringkasan dan detail tiap GH) dipanggil sebagai tiap pengelola aktif, di dalam transaksi yang selalu di-rollback. Jumlah baris di 55 tabel dihitung sebelum dan sesudah. [Output](evidence/backend-readiness-20261118/app-database-readcheck.json); output hanya berisi jumlah, tanpa nama atau email.

- 55 tabel. Migration terakhir `2026_11_17_000022_record_per_greenhouse`. `migrate:status --pending` keluar 0.
- Satu pengelola aktif. Inbox pending 0 dan decided 0, terbaca tanpa error. Beranda terbaca untuk 5 GH, beserta 5 detail GH.
- Status periode berjalan GH: 3 `ready`, 1 `needs_review`, 1 `no_active_period`. Dua GH terakhir perlu ditetapkan pengelola di aplikasi, lewat detail GH → "Ganti tanam saat ini". Ini bukan error.
- Belum ada kegiatan tingkat GH (`planting_group_id` null). Jalur bug inbox 500 yang diperbaiki #81 tidak punya data di sini, jadi pembacaan ini tidak membuktikan perbaikan itu. Perbaikannya dibuktikan oleh tes PHPUnit dan uji perangkat terhadap `inofarm_test`.
- Jumlah baris tidak berubah (`rows_unchanged: true`). Angkanya sama dengan setelah migration 22: `audit_logs` 82, `change_feed` 86, `materials` 50.

**Batas:** ini pembacaan di sisi server, bukan login dari aplikasi. Login dengan akun nyata butuh password pengguna, yang tidak dimasukkan oleh agen.

## 4. Uji restore backup 18 November

Backup: `outputs/rollout-20261118/inofarm_finance_local-20261118-144430.sql` (diabaikan Git).

- **Keaslian:** SHA-256 `5d470ea6…6f74`, sama dengan yang tercatat. Ada 55 `CREATE TABLE` dan penanda "Dump completed".
- **Aman dimuat ke database lain:** tidak ada `USE`, `CREATE DATABASE` atau `DROP DATABASE`, jadi isinya tidak bisa menulis ke `inofarm_finance_local`.

Akun aplikasi `inofarm` tidak punya hak `CREATE DATABASE` (ditolak dengan 1044, pembatasan yang benar). Karena itu restore dilakukan ke **instance MySQL 8.0.30 sementara**: datadir kosong baru di folder scratch, port 3317, `--initialize-insecure`, hanya `127.0.0.1`. Server 3307 hanya dibaca dengan `SELECT`/`CHECKSUM TABLE`. Setelah selesai, instance dimatikan dan datadir-nya dihapus.

| Pemeriksaan | Hasil |
|---|---|
| Waktu restore | 2,3 detik |
| Tabel | 55 = 55 |
| Migration terakhir | 21 di salinan (backup diambil sebelum migration 22), 22 di aplikasi |
| FK | 77 di salinan, 78 di aplikasi. Migration 22 menambah FK `activities.season_id` |
| Jumlah baris `users`, `greenhouses`, `seasons`, `planting_groups`, `varieties`, `grades`, `activities`, `harvests`, `harvest_items`, `sync_operations` | sama |
| `materials`, `audit_logs`, `change_feed`, `entity_identities` | +50 di aplikasi, yaitu 50 bahan semprot, masing-masing dengan audit, feed dan identitas |
| `CHECKSUM TABLE` `users`, `seasons`, `planting_groups`, `varieties`, `grades`, `harvests`, `harvest_items` | **identik** |
| `migrate` pada salinan, hanya di 3317 | migration 22 DONE (568 ms), tidak ada yang tertunda, 2/2 kegiatan mendapat `season_id`, FK 78 = aplikasi |

[Perbandingan](evidence/backend-readiness-20261118/restore-compare.txt), [migrate salinan](evidence/backend-readiness-20261118/restore-migrate.txt).

**Batas:** `inofarm:spray-materials` tidak dijalankan pada salinan. Restore ke server produksi atau ke instance dengan hak terbatas belum diuji. RPO/RTO produksi tetap keputusan Q08.

## Yang tetap terbuka

1. Login dan Beranda dari aplikasi terhadap `inofarm_finance_local` dengan akun pengguna. Pengguna melakukannya sendiri: jalankan `scripts/start-backend-for-studio.ps1`, lalu login di emulator.
2. Dua GH perlu ditetapkan periode berjalannya di aplikasi.
3. Penjaga "minimal satu pengelola aktif" belum diuji lewat API.
4. Server pilot HTTPS, keystore pemilik, keputusan Q07/Q08, UAT dan pentest.
5. Usulan Android E1, E2 dan E4 (label "Memperbarui", pengelompokan Cek catatan panen, EC mentah di form koreksi) belum dikerjakan.
