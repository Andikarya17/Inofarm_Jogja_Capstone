# Audit proyek aktual versus proposal terbaru

Dokumen ini hasil FIN00 sebelum implementasi. Kondisi source sesudah FIN06 ada di [status/evidence](../../context/IMPLEMENTATION_STATUS.md); jangan menggunakan inventaris baseline sebagai klaim v2 belum tersedia saat ini.

Audit statis 29 Oktober 2026. [Sumber dan metode](SOURCES.md); [manifest seluruh file non-ignored](SOURCE_INVENTORY.json). Ini perubahan domain besar, bukan sekadar mengganti judul/warna dashboard. Requirement aplikasi operasional tanpa IoT sudah muncul dalam dokumen desain Oktober; source runnable tetap model lama.

## Perbandingan dan keputusan reuse

| Area | Bukti kondisi aktual | Target proposal | Tindakan |
|---|---|---|---|
| Frontend utama | `android/app/src/main/java/com/inofarm/jogja/MainActivity.kt`, `ui/OwnerApp.kt` | Dua peran, input kegiatan dan keuangan | Pertahankan native project; pecah navigasi/fitur dan ganti dashboard |
| Android state/data | `ui/OwnerViewModel.kt`, `data/OwnerRepository.kt`, `Models.kt` | Model GH/musim/kelompok/transaksi dan state independen | Ganti model domain; reuse pola cancellable request/session generation |
| Cache/offline | `data/ReadCache.kt`: AtomicFile AES-GCM, resources owner, budget2MiB, history500; repository menghapus cache saat expire/logout | Draf dan outbox durable per akun + pull/conflict | Bangun local DB dan sync; jangan reuse clear cache untuk draft/outbox |
| Session/security | `SessionStore.kt`, `ApiEndpoint.kt`, `ApiClient.kt` | Auth pekerja/pengelola, HTTPS/config endpoint dan isolation | Reuse komponen setelah review scope/storage migration; perlu tests baru |
| UI existing | DashboardScreen, HistoryScreen, SettingsScreen, WorkersScreen, DesignSystem/GlassComponents | Jadwal, form, panen, konflik, keuangan, master/admin | Reuse tokens/field/accessibility yang cocok; hilangkan asumsi sensor/owner-only dari flow target |
| Backend auth | `AuthController.php`: role owner/leader + tank_id, Sanctum; `OwnerScope.php` | Pengelola + pekerja assigned GH | Refactor auth response/policy; mapping akun lama eksplisit |
| API | `backend/routes/api.php`: v1 dashboard/history/export/telemetry/config/active-phase/workers/shifts/device | Domain REST + operasi sync/report/recovery | Tambahkan v2, contract tests; jangan ubah makna v1 diam-diam |
| Database | `2026_10_10_000001_create_inofarm.php`: tanks/devices/events/config_versions/phase_versions/workers/worker_versions/shift_versions | Master GH/musim/kelompok, aktivitas/detail, panen, transaksi, aset, report, sync/audit | Migration additive dengan FK/index dan batas agregat; bukan rename tabel |
| Worker identity | workers + shift_versions; `ShiftResolver.php` | Akun worker dan penugasan nyata | Roster lama bukan akun; jangan menganggap nama shift identitas pencatat |
| Validation | `NutrientValidator.php`, Input/ConfigurationStore | Structural validation + comparator configurable per kelompok/waktu; status terpisah | Reuse konsep version snapshot; ganti validasi B sensor/fase dengan service target |
| Idempotensi | `IngestController.php`: eventId/deviceId, canonical hash, tank lock, ACK | Create/update/cancel operation UUID, base_version, receipt dan pull cursor | Reuse prinsip saja; model baru bukan membungkus device event menjadi transaksi |
| Keuangan | Tidak ditemukan model/controller/migration/domain finance runnable dalam inventory | KF07–12 lengkap | Bangun layanan uang/biaya/penjualan/aset/evaluasi/investasi/report |
| File dan restore | Laravel storage/config ada; belum workflow evidence privat/restore target pada route | Attachment auth, PDF/CSV snapshot, DB+file backup/restore | Bangun service/policy, checksum, recovery epoch dan uji restore |
| Firmware | `firmware/src/main.cpp`, include/core/transport, PlatformIO/native test | Tanpa IoT | Bekukan, keluarkan dari release target setelah acceptance; jangan delete sekarang |
| React | `src/App.tsx`, components, utils/kotlinTemplates; root package Vite | Android Kotlin/Compose tetap | Referensi legacy; bukan frontend yang perlu dikembangkan ulang |
| Scripts | build/run/test/package/local-database dan fixture | Run/build/test target dan dokumentasi instalasi | Reuse lingkungan setelah audit; ganti seed/demo/package agar tidak memasukkan perangkat/akun lama |
| Tests | backend Unit/Feature, Android unit/androidTest, firmware native | U01–21 baru | Pertahankan regression baseline selama coexistence; tambah test target, jangan rename test sebagai bukti |
| Dokumen | Context pack mencampur delta Oktober dengan baseline audit lama | Satu baseline aktif + histori terpisah | Context ditulis ulang, versi awal disalin byte-identik ke archive |

## Versi dari source, bukan rekomendasi upgrade

- Android applicationId/namespace `com.inofarm.jogja`; compile/targetSdk37, minSdk 26; AGP9.1.1, Compose compiler plugin2.2.10; Compose BOM2025.08.01; OkHttp4.12.0. Room/WorkManager belum tercantum dependency. Proposal menargetkan Android minimal10: rencanakan minSdk29 dan regression test setelah verifikasi kebutuhan target, tanpa mengganti compile SDK sembarang.
- Backend composer require PHP^8.3, Laravel^13.17, Sanctum^4.0; versi runtime terpasang tidak diuji ulang pada audit ini. MySQL dipilih pengguna; versi server aktual tidak diasumsikan dari dokumen.
- Firmware platformio/espressif32@6.12.0, Arduino, native Unity tests; tidak relevan acceptance target baru.
- React19/Vite8 sesuai manifest root, hanya referensi. Tidak memasang dependency atau menjalankan clean.

## Risiko migrasi prioritas

1. Menganggap cache baca sebagai offline-write menyebabkan kehilangan draft; logout lama menghapus cache. Pisahkan storage semantics sebelum input operasional baru.
2. Reuse owner/tank scope akan menolak worker atau membocorkan GH/keuangan jika hanya menu disembunyikan. Scope seluruh endpoint/file/pull/export dengan policy.
3. Menambahkan uang sebagai Double dan perhitungan dalam Composable menghasilkan drift; fixed decimal dan authoritative domain service wajib.
4. Mengecek saldo tanpa lock memunculkan oversell/over-allocation pada request simultan. Test koneksi MySQL independen wajib, bukan hanya mock sequential.
5. Auto-migrate event perangkat akan mengarang petugas/kelompok/musim. Data lama harus tetap berprovenance legacy sampai pemetaan disetujui.
6. Snapshot laporan yang diekspor ulang dari live query tidak dapat diaudit. Gunakan immutable source/input/result snapshot.
7. Restore tanpa epoch/receipt reconciliation dapat menggandakan atau menghilangkan operasi offline.

## Batas hasil

Tidak ada source aplikasi/database nyata yang diubah dalam task ini. Belum ada schema migration target, APK target atau runtime API target. Hasil build/test 24 Oktober dalam VERIFICATION adalah bukti historis versi lama. Audit statis ini bukan review keamanan menyeluruh atau klaim seluruh branch/remote sudah diperiksa.
