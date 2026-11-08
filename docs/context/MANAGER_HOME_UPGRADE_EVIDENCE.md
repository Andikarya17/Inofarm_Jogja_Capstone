# Bukti upgrade Beranda pengelola

8 November 2026 WIB. Branch `codex/manager-home-overview` berangkat dari spesifikasi `768bf59` dan integrasi menu lanjutan `0ea7f66`. [Master prompt](MASTER_PROMPT_MANAGER_HOME.md) · [Kontrak v2](../../contracts/v2/MANAGER_HOME.md)

Migration 16 menambah satu penunjukan kelompok per greenhouse. Musim `open` tanpa penunjukan tidak otomatis menjadi periode berjalan. Pengelola meninjau greenhouse, varietas, tanggal dan alasan sebelum menetapkan. Menutup atau menonaktifkan parent yang masih ditunjuk memerlukan pengakhiran atau penggantian penunjukan terlebih dahulu. Pengajuan dan audit memakai ledger yang sama. Riwayat dan draf menyimpan parent asal.

Kas bulanan memakai pembayaran aktual aktif dan penjualan lunas aktif pada rentang WIB yang sama. Pembayaran aset dan pembayaran yang belum dialokasikan masuk sekali. Alokasi biaya, penyusutan, panen dan pengajuan lokal berada di luar angka kas. Model belum memiliki biaya bersama lintas greenhouse. Jadwal tampil dari catatan eksplisit. Aturan penyelesaian atau tenggat jadwal baru belum ditetapkan.

## Matriks MH

| ID | Source dan pengujian | Bukti atau batas |
| --- | --- | --- |
| MH-01 | `ManagerHomeTest::test_scope_and_data_driven_counts`, `managerHomeOneTapFiltersRotationAndCachedSnapshot` | Query MySQL mencakup 0 sampai 5 greenhouse. Android memakai daftar lima greenhouse dan masuk tanpa sheet wajib. |
| MH-02 | `RunningPeriodTest`, `ManagerHomeTest`, `managerHomeReadsGuardedMysqlAndDesignatesFromNativeReview` | Kasus ready, no-active dan legacy needs-review. Penetapan dari review Compose direkonsiliasi ke baris dan audit MySQL. |
| MH-03 | `ManagerHomeTest`, `scripts/test-finance-concurrency.php period` | Reopen untuk koreksi tidak menunjuk ulang. Dua koneksi MySQL menguji versi sama. Satu penunjukan diterima dan kedua musim tetap terbuka. |
| MH-04 | `ManagerHomeTest::test_exact_cash_boundaries_and_recent_from_full_scope` | 53 pembayaran November melampaui halaman default 50. Sumber catatan dan kas berasal dari SQL scoped. |
| MH-05 | `ManagerHomeTest`, Compose cache dan perhatian | Snapshot lengkap, acuan berversi, rollover WIB, dan revisi sumber tidak lengkap diuji. Cache offline memberi waktu data terakhir. |
| MH-06 | `FinanceMeasurementTest`, `ManualMeasurementTest`, `FinanceMeasurementStoreTest`, real device test | EC dan PPM terikat satu catatan. Cakupan mencakup fallback dan status manual sesuai kontrak. |
| MH-07 | Real device test dan `ManagerHomeTest` | Baris pengukuran pekerja menunjukkan jenis, greenhouse, pencatat dan waktu kejadian. Ketukan membuka detail lalu Back kembali ke Beranda. |
| MH-08 | `ManagerHomeTest`, `FinanceSaleTest`, rekonsiliasi device | Uang desimal tetap tepat di batas WIB. Rp142,62 dari 53 pembayaran, Rp64,20 dari penjualan, dan 5 kg sebagai hasil panen pada fixture API. |
| MH-09 | `ManagerHomeTest`, Compose cache, device store | Pembatalan aset menurunkan kas keluar menjadi Rp52,52. UI membedakan transaksi nol, kategori tanpa catatan, cache dan data tidak lengkap. |
| MH-10 | `ManagerHomeTest`, `FinanceLocalStoreTest`, Compose revoke/late response, real 403 | Isolasi akun, pekerja, penugasan, epoch, penolakan sesi dan respons lama diuji. Kombinasi demosi dengan setiap jalur unduhan dan ekspor belum diulang end-to-end. |
| MH-11 | `FinanceProcessSurvivalTest`, Compose rotasi dan Back, fixture migrasi SQLite | Parent draf serta ID dan hash outbox bertahan lintas proses. Uji UI terpisah mempertahankan greenhouse dan posisi judul Keuangan pada koordinat yang sama setelah proses mati. |
| MH-12 | Emulator API 30/37, screenshot dan rekaman | Tema, font, landscape, fokus keyboard dan motion on/off diperiksa. Sampel TalkBack mencapai toolbar Beranda. Urutan fokus dan pengumuman suara pada seluruh kontrol belum terverifikasi. |
| MH-13 | `HomeDestination`, Compose filter/Back/login | Pencatatan baru mengikuti konteks Beranda. Tautan riwayat membawa scope dan filter yang berkaitan dengan ringkasan. |
| MH-14 | Regresi API, JVM dan Compose | Alur pekerja mempertahankan pemilih konteks, draf/outbox, pengukuran dan panen. |

## Bukti perangkat

Output lokal di `outputs/manager-home/`. Baseline memakai APK debug terisolasi dari `768bf59` pada AVD `Inofarm_Botanical_API30`, 1080 × 2340, 440 dpi, skala font 1,0 dan tema gelap. Hash APK dan log ada di `before/`. `manager-home.png` dan `worker-home.png` berada di folder `before/frontend-secondary-manager-home-before/`. `before/manager-home-with-context.png` diambil terpisah pada APK baseline dengan cache sintetis. Tes baseline berhenti saat menuju tab Keuangan setelah tangkapan awal. Berkas `manager-home-selected.png` dari tes itu berisi form sehingga tidak dipakai sebagai baseline ringkasan.

Run nyata Android → Laravel → MySQL mengisolasi akun, record, APK dan perangkat uji. Pekerja membuat pengukuran 1,25 mS/cm dan 625,50 ppm. Pengelola menetapkan periode dan mencatat pembayaran aset Rp100,01. `after/device-real-delivery.log`, `after/sql-reconciliation-delivery.log` dan PNG di `after/frontend-secondary-manager-home-delivery/` membuktikan jalur tersebut. JSON snapshot dari perangkat lolos validasi OpenAPI. Data ini sintetis. Run MockWebServer memverifikasi perilaku Compose.

- Backend memakai guard MySQL 8.0.30 `inofarm_test`. Suite lulus 135 tes dan 2.535 assertion. Uji konkurensi periode lulus dengan dua proses dan koneksi independen. Lihat `backend-delivery.log` dan `mysql-concurrency-delivery.log`.
- Android build standar, build instrumentasi, 73 tes JVM dan lint lulus. Lint melaporkan 0 error, 16 peringatan dan 1 hint. Run Compose gelap lulus 6 tes. Run cache dan pengukuran lulus 28 tes termasuk dua kasus Compose. Fase persiapan dan verifikasi draf lintas proses masing-masing lulus 1 tes. Lihat log `build-standard-final`, `device-dark`, `device-candidate`, `process-prepare`, dan `process-verify` di `after/`.
- API 30 tema terang pada 320 dp dan font 200% lulus dua tes dengan tiga skala animasi Android disetel ke 0. PNG di `after/frontend-secondary-manager-home-320-reviewed/` menampilkan nominal panjang, sumber pengukuran, keadaan offline dan daftar greenhouse. Uji awal pada konfigurasi ini perlu menggulir ke kontrol di luar viewport dan mengenali sesi yang ditolak.
- Tema terang pada 412 dp dan font 130% diperiksa pada perangkat, termasuk pemilih dan fokus keyboard. `after/manager-home-delivery-motion.mp4` merekam pergantian cakupan dan pembukaan sumber dengan motion aktif. `after/ui-process-death.log` mencatat PID 8270 menjadi 8597 dengan posisi judul `[88,1199][461,1251]` sebelum dan sesudah.
- API 37 lulus dua tes untuk layout landscape dan respons scope tertunda. Bukti `after/device-api37.log` dan `after/frontend-secondary-manager-home-api37/`. Empat pasangan warna teks dan permukaan lolos pemeriksaan kontras dengan rasio 6,04:1 hingga 11,52:1. Rekaman emulator menilai perilaku transisi dan reflow, tanpa benchmark frame produksi.
- TalkBack dan touch exploration aktif di API 37. Fokus pada toolbar terlihat di `after/talkback-overview-7.png`. Pengaturan aksesibilitas dan izin notifikasi dipulihkan. Sampel debug dengan TalkBack mencatat 26 frame janky dari 29 frame di `after/gfxinfo-api37.txt`. Sampel ini belum mengisolasi biaya transisi atau menggambarkan kinerja perangkat fisik.

## Menjalankan ulang

- Backend `./scripts/test-backend.ps1` memasang guard sebelum SQL. Jalankan concurrency dengan `.tools/php/php.exe scripts/test-finance-concurrency.php period` hanya setelah suite selesai. Keduanya memakai MySQL `inofarm_test`.
- Android `./scripts/build-android.ps1 -JavaHome <JDK> -Tasks @(':app:assembleDebug', ':app:assembleDebugAndroidTest', ':app:testDebugUnitTest', ':app:lintDebug')`. Untuk paket verifikasi terisolasi, tetapkan `ORG_GRADLE_PROJECT_performanceTest=true`.
- HTTP perangkat memakai `scripts/finance-device-fixture.php create manager-home` dengan `FINANCE_DEVICE_TEST_BASE_URL=http://127.0.0.1:8001/api/v2/`, `scripts/start-test-backend.ps1 -Port 8001` dan `adb reverse tcp:8001 tcp:8001` pada emulator uji. Berikan manifest sintetis sebagai Base64 dalam argumen instrumentasi `managerHomeFixture`, dengan `devicePhase=manager-home`, kepada `FinanceBackendDeviceTest#managerHomeReadsGuardedMysqlAndDesignatesFromNativeReview`. Jalankan `verify-manager-home manager-home` dan `cleanup manager-home` melalui script fixture. Jangan jalankan suite PHPUnit bersamaan dengan fixture HTTP.

## Batas

Belum ada UAT mitra, pentest, benchmark perangkat fisik atau deployment. Data produksi, server, dan migrasi basis data pengguna berada di luar verifikasi.
