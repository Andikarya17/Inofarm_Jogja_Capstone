# Rancangan backend rombak pencatatan (migration 22), 17 November 2026

Status: **disetujui pengguna 17 November 2026** ("Setuju, mulai F0"), termasuk penyesuaian `FieldReports`. Implementasi F1 ada di branch `backend-record-per-greenhouse`; perbedaan dari rancangan dicatat di bagian akhir. Melengkapi [handoff](HANDOFF_RECORDING_REWORK_20261117.md); jawaban pengguna 17 November di bawah mengatasi K2/K4 handoff bila bertentangan.

## Jawaban pengguna (17 November 2026)

| Pertanyaan | Jawaban |
|---|---|
| Bentuk form Catat kegiatan | Satu halaman memuat semua bagian (K4) |
| Per GH atau per varietas | Pengukuran, nutrisi, pekerjaan umum, stadia: **satu catatan per GH**. Hanya **penyemprotan** dibedakan per varietas (satu tandon per GH) |
| HSS/HST manual | **Wajib** |
| Letak pengaturan tema | Menu avatar di app bar, kedua peran |
| Cek catatan | Mengikuti model per GH: satu kartu per isian (lihat D6) |

Akibatnya, usulan handoff K4 "satu catatan per kelompok" **tidak dipakai**: menyalin pengukuran/nutrisi ke dua kelompok akan menggandakan pemakaian bahan.

## D1. Migration 22 `2026_11_17_000022_record_per_greenhouse.php` (additive)

`activities`:

| Kolom | Tipe | Aturan |
|---|---|---|
| `season_id` | uuid FK `seasons`, restrict | Diisi untuk semua baris. Baris lama di-backfill dari `planting_groups.season_id` (kolom baru turunan; nilai lama tidak diubah) lalu dibuat NOT NULL |
| `planting_group_id` | jadi **nullable** | Wajib untuk `spray`; kosong untuk catatan tingkat GH. Baris lama tetap terisi |
| `entry_batch_id` | uuid nullable, index | Mengelompokkan catatan dari satu isian form |
| `age_basis` | varchar(3) nullable `hss`/`hst` | Berpasangan dengan `age_day` |
| `age_day` | smallint unsigned nullable, ≥ 1 | |
| `spray_date` | date nullable | Hanya `spray`; tanggal penyemprotan terpisah dari `occurred_at` |
| `spray_period` | varchar(10) nullable `morning`/`afternoon` | Hanya `spray`; tanpa jam karangan. Urutan riwayat: `spray_date`, pagi sebelum sore |

Index baru `(season_id, occurred_at, id)`.

`activity_materials`: `unit_id` dan `quantity` jadi **nullable** (dosis semprot opsional; nutrisi tetap wajib di validasi service).

`materials`: kolom `purpose` varchar(20) nullable (`spray` atau null). Unique fungsional tidak dipakai; penjaga duplikat ada di service (lihat D3) di dalam transaksi dengan lock.

Tidak ada baris lama yang dihapus atau nilainya diubah. `down()` hanya menghapus kolom baru dan mengembalikan NOT NULL bila semua baris masih terisi.

## D2. Payload activity

Create/update tetap envelope `activity`, satu operasi per catatan. Bentuk baru (dikirim Android baru):

```json
{
  "season_id": "uuid",              // wajib bila planting_group_id kosong
  "planting_group_id": "uuid|null", // wajib untuk spray, null untuk tipe lain
  "entry_batch_id": "uuid",
  "activity_type": "measurement|nutrition|general|stage|spray",
  "occurred_at": "2026-11-17T08:00:00+07:00",
  "age_basis": "hss|hst", "age_day": 30,
  "spray_date": "2026-11-17", "spray_period": "morning|afternoon",
  "materials": [
    {"material_id": "uuid", "unit_id": "uuid|null", "quantity": "string|null"},
    {"material_name": "Bion M", "unit_id": null, "quantity": null}
  ],
  "manual_ec": "...", "manual_ppm": "...", "manual_ph": "...", "observations": {...},
  "actual_stage": "...", "work_state": "completed", "notes": ""
}
```

- **HSS/HST wajib** bila `entry_batch_id` ada (bentuk baru). Payload lama tanpa batch (draf/outbox lama berisi `planting_group_id`) tetap diterima tanpa HSS/HST, agar antrean lama tetap terkirim.
- `season_id` diturunkan dari kelompok bila hanya `planting_group_id` yang dikirim (payload lama). Bila keduanya ada, harus cocok.
- `spray_date`/`spray_period` wajib untuk spray bentuk baru, ditolak untuk tipe lain.
- Satu isian form → satu operasi per bagian yang terisi: `measurement` (GH), `nutrition` (GH), `stage` (GH), `general` (GH, bila hanya catatan), dan `spray` **per varietas yang dipilih**. Semua berbagi `entry_batch_id`, `occurred_at`, `age_basis`, `age_day`.
- `schedule_id` untuk catatan tingkat GH selalu null (jadwal per kelompok tidak dipakai di form baru).
- `work_state: skipped` tetap diterima untuk payload lama; form baru tidak lagi mengirimnya.

## D3. Bahan semprot

- Item `materials` boleh `material_name` (trim, 1–150 karakter) **sebagai pengganti** `material_id`, hanya untuk `spray`.
- Server find-or-create: cari bahan `purpose=spray` di GH itu dengan `LOWER(TRIM(name))` sama; bila tidak ada, buat bahan baru (`purpose=spray`, `dimension='spray'`) **di setiap GH**, sehingga muncul di dropdown semua GH. Lock `greenhouses` baris terkait agar dua kiriman bersamaan tidak membuat duplikat. Bahan baru masuk change feed (audience all), jadi pengguna lain menerimanya lewat sync.
- Retry operasi yang sama tetap idempoten lewat ledger (hash payload memuat nama, bukan id hasil).
- Dosis opsional untuk spray: `unit_id` dan `quantity` keduanya ada atau keduanya null. Untuk bahan `purpose=spray`, kecocokan dimensi bahan/satuan **tidak** diperiksa (satuan bebas, misalnya ml/L). Nutrisi tidak berubah: id, satuan, jumlah wajib dan dimensi harus sama.
- Daftar awal (keputusan pengguna, bukan makalah): Nisozin, Demolish, Regent, Antracol, Agrept, Amistartop, Bion M, Daconil, Besromil, Sop 3g/L. Disemai **idempoten** oleh migration 22 untuk setiap GH yang ada, dan oleh pembuatan GH baru (wizard / `POST greenhouses`), lewat satu helper `SprayMaterialCatalog::ensure($greenhouseId)`.

## D4. Akses (K3)

- `GreenhouseAccess::ids/query` untuk `worker`: semua GH **aktif**, tanpa penugasan. Manager tidak berubah.
- `manageableUsers` untuk non-manager tidak lagi bergantung pada penugasan (non-manager tetap tidak memanggil endpoint akun; guard `manager()` tidak berubah).
- Keuangan tidak berubah: service keuangan sudah memakai guard `manager()`. Tes baru memastikan pekerja tanpa penugasan menerima 403 di setiap endpoint keuangan (expenses, cost-allocations, sales, assets, depreciation, season/investment reports, exports, investment-scenarios, field reports) dan tidak menerima entity keuangan di `sync/changes`.
- Feed sync: `scopeKey` sudah memuat daftar GH, jadi perubahan cakupan memutar ulang feed dari awal tanpa menyentuh antrean lokal. Tes ditambah untuk pekerja lama yang mendapat GH baru.
- Tabel/endpoint `assignments` tetap ada untuk kompatibilitas, tidak menentukan akses.

## D5. Pembacaan yang ikut menyesuaikan

- `ActivityService::scope`: lewat `season_id` → seasons dalam akses, bukan lewat kelompok.
- `GroupContext` mendapat padanan `SeasonContext` (GH aktif, musim terbuka) untuk catatan tingkat GH.
- Snapshot acuan untuk catatan tingkat GH: umur `PlantAge` tidak lagi menolak (K6: umur manual yang ditampilkan); target EC/PPM/pH dibandingkan bila musim punya tepat satu kelompok aktif, selain itu `unavailable` dengan alasan "Target per varietas; catatan tingkat greenhouse tidak dibandingkan".
- `ManagerHome` (stadia terakhir, pengukuran terakhir) dan `measurements/latest` menerima catatan tingkat GH, plus umur manual terakhir per GH (`age_basis`, `age_day`, tanggal isi) untuk K6.
- `FieldReports` pemakaian bahan per musim: join lewat `activities.season_id` agar nutrisi tingkat GH ikut terhitung; baris dosis kosong ditampilkan sebagai jumlah null. Ini laporan lapangan, bukan model biaya; tidak ada perubahan pada biaya/alokasi/penjualan/aset (K7). **Butuh persetujuan karena file berada di folder `Domain/Finance`.**
- Cek catatan (D6): tidak ada endpoint baru. Android mengelompokkan per `entry_batch_id` dan mengirim satu `activity-reviews` per catatan dalam batch dengan keputusan dan alasan yang sama. Server tetap menyimpan review per catatan/revisi.

## D6. Kontrak dan tes

- `contracts/v2`: OpenAPI, schema activity/request/feed, fixture baru (catatan GH, semprot dua varietas, bahan dari nama, payload lama).
- Tes backend baru: migration 22 (backfill, kolom, seed idempoten), validasi bentuk baru/lama, find-or-create tanpa duplikat (huruf besar/kecil, spasi, paralel lewat replay), dosis opsional, akses pekerja tanpa penugasan, keuangan ditolak per endpoint, replay feed saat scope berubah, FieldReports dengan catatan GH.
- Hanya `inofarm_test`. **Migration 22 ke `inofarm_finance_local` tidak dijalankan** tanpa izin dan backup terpisah.

## Di luar F1

Wizard (F2) tetap memanggil endpoint yang ada; satu kelompok per varietas dibuat Android dengan nama varietas. Tidak ada endpoint wizard baru.

## Hasil F1 dan perbedaan dari rancangan (17 November 2026)

- **Daftar awal bahan semprot tidak disemai oleh migration.** Setiap material butuh audit dengan aktor nyata, dan migration tidak punya aktor. Daftar dibuat otomatis saat `POST greenhouses` (aktor: pengelola pembuat) dan untuk greenhouse lama lewat `php artisan inofarm:spray-materials <email-pengelola>`, idempoten. Untuk `inofarm_finance_local` perintah ini dijalankan setelah migration 22, bersama izin dan backup.
- **Pekerja membaca laporan pemakaian bahan per musim** (`seasons/{id}/material-usage`) di semua GH aktif. Ini perilaku lama yang dulu dibatasi penugasan. Laporan itu bukan keuangan, jadi tidak diubah (K7); bila harus khusus pengelola, perlu keputusan terpisah.
- **GH nonaktif keluar dari scope pekerja**, sehingga replay pekerja ke GH yang dinonaktifkan sebelum antrean terkirim mendapat 404 (dulu receipt lama tetap dikembalikan bila penugasan masih aktif). Pengelola tidak terpengaruh.
- **Beranda pengelola** mengisi kartu GH dari musim kelompok periode berjalan. Bila GH punya dua varietas, salah satunya tetap harus ditetapkan sebagai periode berjalan (aturan lama); catatan tingkat GH dibaca dari musimnya, apa pun varietas yang dipilih.
- Tes `FinanceAuthScopeTest` lama ternyata menguji akun pekerja dua kali karena guard tidak di-reset setelah login; diperbaiki bersama aturan baru.
- Android **belum** aman terhadap `planting_group_id: null` dan kolom baru; jangan memasang backend ini untuk aplikasi lama sebelum F3–F4 selesai.

Pemeriksaan: backend 173 tes/4.724 assertion lulus di `inofarm_test` (167 lama, 6 baru), guard 4 tes, checker kontrak dan parity OpenAPI lulus. Migration 22 **belum** dijalankan pada `inofarm_finance_local`.
