# Data Contracts Model Integrasi dan Ownership

## Instalasi tim dan baseline GitHub — 24 Oktober 2026

Tidak ada perubahan endpoint/payload/schema atau parameter agronomis. Panduan baru membuat credential/database lokal sendiri; file sensitif tidak dipublikasikan. Demo dan full suite tetap dibatasi MySQL lokal port3307 sesuai guard, testing hanya inofarm_test. [Evidence](../implementation/GITHUB_PUBLICATION.md).

## Rancangan ulang tanpa IoT — 21 Oktober 2026

Model target baru disepakati untuk diagram: GH → musim bernama bebas → kelompok varietas → jadwal/aktivitas/panen. Keuangan memisahkan pengeluaran/alokasi, aset/penyusutan, penjualan/detail panen serta skenario/arus kas tahunan. UUID objek tetap; operation_id per mutasi dan base_version menangani retry/konflik. Registry objek/audit merupakan rancangan teknik, bukan migrasi yang sudah diterapkan. Kontrak konseptual, cardinality, sumber dan aturan kelengkapan dirinci dalam [paket diagram proposal](../design/mobile-greenhouse-20261021/README.md). Endpoint dan payload implementasi baru belum dibuat; kontrak lama di bawah tidak otomatis menjadi kontrak target baru.

## Data demo lokal — 20 Oktober 2026

Kontrak existing dipakai tanpa enum baru:30 record dan30 telemetry dummy; status valid3/issues17/incomplete10,14 skenario pada body.notes/checks, assignment scheduled/unscheduled/time_unknown. Waktu sumber sintetis historis, received/evaluated waktu provisioning; clock-invalid source_at null. Angka fixture bukan ambang produksi. Detail [DEMO_DATA](../implementation/DEMO_DATA.md).

## Publikasi Git per fitur — 15 Oktober 2026

Pengguna mengotorisasi commit per fitur dan push ke GitLab UMY `andika.arya.ft23/inofarm_jogja`, memakai identitas Andika Arya Putra dan email kampus yang diberikan. Riwayat remote `main` beserta konfigurasi SAST/Secret Detection dipertahankan; impor snapshot lokal dikerjakan pada branch `codex/project-import` dan disusun menjadi commit berdasarkan fitur. Ini pengelompokan source yang sudah ada, bukan rekonstruksi tanggal pengerjaan. Environment, credential, cache IDE, runtime, log dan artefak build tidak dipublikasikan. Verifikasi lokal dan prosedur ada di [GIT_WORKFLOW](../implementation/GIT_WORKFLOW.md); tidak mengubah requirement/kontrak/aturan agronomis dan tidak menutup gate hardware, pentest atau handover.

## Konsistensi filter UI — 15 Oktober 2026

Tidak ada perubahan payload/endpoint, producer atau aturan validasi. Pemilihan tab Filter dan pengeditan draft tidak memanggil query/ekspor dengan draft; keduanya tetap memakai appliedFilters. Shortcut Hari ini/7 hari menerapkan rentang tanggal WIB dan mempertahankan status applied, bukan status draft yang belum disubmit. Terapkan memakai draft tervalidasi; Reset menerapkan semua catatan dan membersihkan draft/error dengan pilihan UI tetap Filter. Pemilihan/panel/draft bertahan pada perpindahan tab dan Activity recreation dalam pengujian lokal; ini bukan bukti process death. Bukti: VERIFICATION / outputs/ui-navigation-20261015/README.md.

## Tooling run ulang — 15 Oktober 2026

Launcher/petunjuk Running Devices/konfigurasi specified MainActivity diperbaiki tanpa perubahan endpoint/payload/account/cache/aturan produksi. Transcript lokal hanya output build/launch, tidak menyimpan credential. Run ulang build/install/start VERIFIED_LOCAL; detail VERIFICATION.

## Endpoint aplikasi lokal — 14 Oktober 2026

Default APK debug emulator http://10.0.2.2:8000/api/v1/; launcher USB127/reverse. Production release/profile endpoint tetap belum ditetapkan kecuali property HTTPS. Alamat login berada pada disclosure koneksi, tanpa credential terbundel. Legacy127/localhost8000/api/v1 dimigrasikan hanya pada login debug emulator; server lain/port/path lain dan scope sesi aktif dipertahankan. Semua payload/login/auth/tandon tetap kontrak existing. Android17 permission debug memungkinkan akses default emulator; live login/backend-read PASS tanpa reverse. Detail VERIFICATION.

## Tooling lokal — 14 Oktober 2026

Follow-up: satu akun owner lokal dibuat sesuai permintaan eksplisit pengguna, name Inofarm dan tank-linked/password hashed; login/dashboard existing PASS. Tidak ada schema/endpoint/role baru atau credential dalam source/docs/APK. Detail VERIFICATION.

Launcher Android memakai URL debug existing http://127.0.0.1:8000/api/v1/ melalui ADB reverse8000. Tidak memasukkan credential akun/database ke APK, menambah payload/endpoint/fixture atau mengubah parameter produksi. Database lokal melalui script provisioning existing; tidak menjalankan test/migration/clear data. Build/launch/health lokal terverifikasi, autentikasi pengguna tidak diuji dalam task launcher.

## Delta konsumsi data/cache Android — 13 Oktober 2026

Tidak ada perubahan endpoint, payload mutation, rule B, jadwal, threshold, assignment atau snapshot historis. History memakai next_cursor dan prev_cursor opaque dari response Laravel existing; keduanya diteruskan sebagai query cursor, tidak dibuat client. Jendela maksimal4×25 row tidak mengubah batas akses history/CSV; cache500 tetap berbeda dari pagination live. Pagination cache tidak menyediakan cursor.

Cache lokal schema2 mengenkripsi scope, fetchedAt, payload dan filters per resource. Total seluruh file termasuk staging pengganti dibatasi2MiB; update oversize/failure mempertahankan resource baik sebelumnya. Schema1 diinvalidasi tanpa menghapus sesi; sekali transisi offline belum tentu mempunyai data cache hingga server berhasil dimuat. Semua provenance/clock/reasons tetap, cache selalu last-known.

Sesi memori tetap mengikuti expiry server, clock mundur, logout/401 dan session generation. Response yang tiba setelah known expiry tidak boleh diterbitkan. Backend tetap authoritative; offline revocation yang belum diketahui tetap baru terdeteksi saat401, sesuai kebijakan existing. HTTP release tetap ditolak dan client default tidak mempercayai sertifikat mock TLS. Contract/cancellation/cache regression diuji pada debug dan profile teroptimasi; sertifikat fixture hanya dalam APK tes. [API](../implementation/API.md), [ANDROID_PERFORMANCE](../implementation/ANDROID_PERFORMANCE.md).

## Presentation Liquid Glass tanpa perubahan API — 12 Oktober 2026

Sembilan view menggunakan DTO dan endpoint existing. Pemisahan editor tidak mengubah PUTconfig penuh menjadi partial update; ConfigDraft/json/serialize dan baseVersion dipertahankan. Back mempertahankan draft; Batal checkpoint bagian yang sedang diedit; reload server explicit. Selector PPM tidak memanggil active-phase. Worker/shift tetap memakai ID/version/key serta matchingACK; overlap/assignment tetap backend authoritative. Waktu ringkas WIB didampingi disclosure/provenance penuh; seluruh checks tetap ada. Baris jadwal kosong belum disentuh bukan row tersimpan. Fixture hanya androidTest APK, tidak bundel produksi/backend. Contract API.md tidak diedit. [VERIFICATION](../implementation/VERIFICATION.md).

## Presentasi tanpa perubahan kontrak — 11 Oktober 2026

Redesign tidak mengubah endpoint/payload/enum/rule/snapshot. Kualitas sensor dipresentasikan dengan label Bahasa Indonesia, tanpa mengartikan ok sebagai normal agronomis. Source capture, contact, receipt, server check/fetch tetap berbeda; loading/last-known tidak memberi klaim kontak baru. Clock kiriman terbaru yang tidak dipercaya tampil langsung; bacaan sumber terpercaya tetap menggunakan pemilihan existing.

Riwayat menonjolkan manualPpm, fase ESP32, konfirmasi, pekerja terjadwal dan semua checks. Disclosure hanya mengelompokkan measurement/receive/evaluation, fase owner dan versi; tidak membuang data atau reasons. Draft filter tetap berbeda dari appliedFilters untuk daftar/ekspor. Helper/error field memakai batas existing dan tidak mengisi nilai produksi. Fixture RedesignUiTest hanya instrumentation, bukan fallback APK. [UI_REDESIGN](../implementation/UI_REDESIGN.md).

## Delta kontrak T1–T7

Login menambah expiresAt dan user.id/tank_id stabil. Dashboard telemetry/latestRecord memakai source_at terpercaya terbaru; latestTelemetryReceipt/latestRecordReceipt menampilkan receipt terbaru termasuk invalid clock/replay. Source/capture/receipt/serverTime/fetch/quality tetap berbeda. PUT config mengembalikan {config:version}, PUT active-phase {activePhase:version}. Days jadwal unik per row; hari sama lintas row diterima. Technical ranges Android diselaraskan API tanpa nilai produksi default.

Cache schema 1 mempertahankan payload/provenance/history checks dan applied filters, scoped server-account-tank, dedupe immutable ID/max500/max2 MiB; cached cursor null. Matching ACK action berbeda dari status refresh. Logout/401/known expiry menghapus snapshot. Firmware UUID/body immutable hingga persisted ACK cocok; malformed/rejected payload retained dengan blocked marker. Lihat [API](../implementation/API.md) untuk bentuk aktual, [VERIFICATION](../implementation/VERIFICATION.md) untuk evidence.

> **Kontrak aktual 10 Oktober 2026:** pengguna mengizinkan implementasi lintas Kotlin/Laravel/MySQL/ESP32. Model TypeScript/draft di bawah adalah baseline historis. [API.md](../implementation/API.md) mendeskripsikan endpoint/field/nullability/units/auth/limits/schema aktual; [DECISIONS](../implementation/DECISIONS.md) mencatat pilihan teknis reversible dan [VERIFICATION](../implementation/VERIFICATION.md) batas pembuktiannya.

## Delta kontrak yang telah diimplementasikan

- `schemaVersion:"1"`, UUID event/device, clockSource/clockStatus, measuredAt/confirmedAt/capturedAt nullable RFC3339; server received_at/evaluatedAt terpisah. Tidak mengisi measurement handheld yang tidak diketahui atau mengganti confirmation dengan receive.
- Empat readings + quality wajib; angka finite iff quality ok. Sensor missing/failed/uncalibrated null, bukan normal. manualPpm DECIMAL kapasitas teknis maksimal tiga desimal, editor perangkat enam digit bulat.
- Backend source_at/clock_trusted mengikuti deklarasi clock tersedia/nonfuture; raw waktu di body tidak hilang. Ini bukan bukti RTC akurat. Source timezone dipertahankan secara semantik, database/session UTC, UI/shift Asia/Jakarta.
- Konfigurasi dan fase aktif dua version stream, initial null, optimistic baseVersion, historical as-of confirmation. Event memakai configSnapshot/ownerPhaseSnapshot/config_version_id/phase_version_id/ruleVersion B-1, tanpa revalidation diam-diam.
- Shift recurrence versioned ISO weekdays/hari mulai, [start,end), lintas malam, overlap ditolak. Assignment workerNameSnapshot/shiftSnapshot immutable, label pekerja terjadwal. Tanpa waktu/jadwal tidak menebak nama; rename/deaktivasi tidak mengubah replay historis.
- Semua check/result/code/message dipertahankan. Incomplete mendominasi badge tanpa menghapus issue; warning mismatch ikut issues ketika lengkap tanpa menolak input. ACK persisted hanya sesudah transaction; duplicate identik sama, isi berbeda 409.
- History cursor berdasarkan sequence receipt immutable; date filter WIB memakai source terpercaya, export CSV bounded dan formula-safe. CLI credentials private/ignored, actor scope server-derived; tidak ada DB credentials di APK.

Audit 10 Oktober 2026. Tidak ada API endpoint, database deployment, MQTT broker, local Room atau Kotlin domain model aktif. Empat label wajib: **VERIFIED EXISTING** = source saat ini; **PROPOSAL-DERIVED** = entity/field dari proposal belum diimplementasikan; **PROPOSED** = desain engineering draft; **UNCONFIRMED** = belum diputuskan. Sumber P/G/S dan Q mengacu [PRD](PRD.md). Tidak boleh membuat migration/service dari draft ini tanpa task approved.

**CONFIRMED DECISION — pengguna, 10 Oktober 2026:** database menggunakan **MySQL**. Keputusan engine bukan VERIFIED EXISTING deployment atau persetujuan semua field draft. Versi/environment MySQL, framework backend, tipe fisik final, precision/nullability, constraints, migration dan API tetap menunggu keputusan relevan Q-11/TASK-004. Credential/koneksi MySQL hanya pada backend, bukan aplikasi owner.

## 1. VERIFIED EXISTING TypeScript contracts

Lokasi `src/types/index.ts`. Semua `number` JS dapat mengandung NaN/Infinity tanpa runtime guard; static type bukan validasi data tersimpan. `?` berarti optional/undefined, bukan contract nullable server. JSON.parse tidak memvalidasi shape.

| Model/type | Field → type / optionality | Unit/semantics actual |
|---|---|---|
| PlantPhase | `'vegetatif'` atau `'generatif'` | Literal lower-case; tidak ada batch/umur/fase history |
| IoTStatus | `'online'`, `'offline'`, `'reconnecting'` | Type tersedia, Dashboard memakai union lokal online/offline; bukan state remote |
| SensorReading | waterTemp:number; ph:number; roomTemp:number; humidity:number; timestamp:string (semua required) | °C; pH; °C; %; timestamp display text, tanpa deviceId/captureTime/source/quality |
| NutrientLogStatus | `'valid'`, `'late'`, `'low'`, `'high'` | Tidak memuat belum_tervalidasi atau data_sensor_abnormal |
| NutrientLog | id:string; dateStr:string; timeStr:string; timestamp:number; ppm:number; phase:PlantPhase; status:NutrientLogStatus; statusLabel:string; notes?:string; recordedBy?:string | timestamp epoch ms saat submit/fixture; dateStr/timeStr display; PPM manual; recordedBy free text, bukan FK identity |
| ValidationSettings | morningSchedule:string; afternoonSchedule:string; toleranceMinutes:number; activePhase:PlantPhase | HH:mm string; menit; activePhase juga dipakai editor range; tidak versioned/device-scoped |
| ValidationSettings.ppmRanges | vegetatif:{min:number,max:number}; generatif:{min:number,max:number} | PPM; kedua fase dipersist lokal; no explicit cross-field validation |
| ValidationSettings.sensorThresholds | waterTemp:{min:number,max:number}; ph:{min:number,max:number} | °C/pH; tidak roomTemp/humidity despite ERD |
| TabKey | `'beranda'`, `'riwayat'`, `'pengaturan'` | Parent UI tab, bukan serialized route |
| KotlinCodeFile | filename:string; category:'Screens'/'UI Theme'/'Model & Logic'/'Config'; description:string; code:string | `src/utils/kotlinTemplates.ts`; generator-like fixed template inventory, bukan compiled code |

`validateNutrientInput(timeStr:string, ppm:number, phase:PlantPhase, settings:ValidationSettings)` mengembalikan `{status:NutrientLogStatus,statusLabel:string,reason:string}`. Processing actual: parse HH:mm menjadi menit → nearest dari pagi/sore dengan absolute difference → bandingkan toleransi → range PPM phase → pilih low/high sebelum late kemudian valid. Ini **VERIFIED EXISTING behavior**, tidak formula proposal final. Tidak mengecek sensor/date/RTC/device/record ID/parameter version/finite number. Probe NaN→valid mendukung GAP F-007.

Current local lifecycle: App init parse `inofarm_settings`/`inofarm_logs` dari localStorage atau pakai fixtures; useEffect overwrite JSON setelah state berubah. Create log prepend ke array; tidak ada update/delete log. Settings overwrite; logs terdahulu tidak direvalidasi otomatis. Tidak ada schemaVersion/migration/retention/bounded history/cross-device sync. Sensor/reset on reload; unread count reset; status device lokal component reset saat navigation. Storage write failure console-only, toast form tetap sukses.

Kotlin **templates** mengimport `NutrientLog` dan `ValidationResult` dari package data.model, tetapi tidak ada deklarasi class yang diberikan. Referenced properties NutrientLog: id/dateStr/timeStr/ppm/phase/status; intended types tidak seluruhnya dinyatakan. Referenced ValidationResult constructor: status/label/isValid/recommendation. Jangan mengklaim Kotlin data class atau ABI tersedia. Template validator memakai LocalTime, ppm:Int, phase:String, jadwal LocalTime, toleransi Long; rentang hardcoded dan invalid phase masuk cabang generatif. Itu bukan approved domain schema.

## 2. PROPOSAL-DERIVED database/entity model

Sumber utama P §2.2 **Gambar 2.5 ERD** dibaca langsung; Class Gambar 2.4/narasi sebagai pembanding. Sebelas nama berikut adalah desain proposal, **bukan tabel aktual**. Tipe mengikuti gambar (INT/VARCHAR/FLOAT/TEXT/ENUM/TIME/DATE/DATETIME/BOOLEAN). **Nullability, length, unsigned, defaults, precision, unique constraints, FK deletion policy, indexing, timezone/retention tidak ditentukan**; jangan menyimpulkannya dari tidak adanya tanda nullable pada gambar. PK/FK label preserved. Semua entity belum deployed.

| Entity proposal | Field dan tipe dari ERD | Relasi/ownership/lifecycle |
|---|---|---|
| users | id_user:INT PK; nama:VARCHAR; username:VARCHAR; password:VARCHAR; role:ENUM(pekerja,pengelola); no_telepon:VARCHAR; created_at:DATETIME; updated_at:DATETIME | User menerima notifikasi, mengatur jadwal/parameter dan membuat pencatatan. Owner/admin identity backend; tidak menentukan worker identification pada perangkat. Password VARCHAR tidak menyatakan plaintext; implement hash sesuai SOP. |
| perangkat_iot | id_perangkat:INT PK; nama_perangkat:VARCHAR; lokasi:VARCHAR; status_perangkat:ENUM(aktif,nonaktif,error); last_online:DATETIME; created_at:DATETIME; updated_at:DATETIME | Device menghasilkan data_sensor, memiliki sensor, backup_lokal dan riwayat_monitoring; metadata device bukan switch client. Provisioning/heartbeat policy TBD. |
| jadwal_nutrisi | id_jadwal:INT PK; id_pengelola:INT FK; nama_jadwal:VARCHAR; fase_tanaman:ENUM(vegetatif,generatif); waktu_jadwal:TIME; hari_aktif:VARCHAR; toleransi_menit:INT; status_aktif:BOOLEAN; created_at:DATETIME; updated_at:DATETIME | Owner mengatur; menjadi acuan record/notifikasi. Recurrence hari_aktif format/timezone/schedule occurrence TBD. Class waktuJadwal datetime berbeda ERD TIME. |
| sensor | id_sensor:INT PK; id_perangkat:INT FK; jenis_sensor:ENUM(suhu_air,ph_larutan,suhu_ruangan,kelembapan_ruangan); satuan:VARCHAR; status_sensor:ENUM(aktif,nonaktif,error); nilai_minimum:FLOAT; nilai_maksimum:FLOAT; created_at:DATETIME; updated_at:DATETIME | Device owns sensor metadata; DHT22 menghasilkan dua jenis nilai, bukan berarti dua physical devices. Source konfigurasi bounds sensor vs parameter_validasi perlu klarifikasi. |
| data_sensor | id_data_sensor:INT PK; id_perangkat:INT FK; waktu_baca:DATETIME; suhu_air:FLOAT; ph_larutan:FLOAT; suhu_ruangan:FLOAT; kelembapan_ruangan:FLOAT; status_data:ENUM(normal,abnormal); created_at:DATETIME | Device captured telemetry; record nutrient dapat merujuk snapshot terakhir melalui id_data_sensor. Missing/invalid value dan age batas belum dimodelkan di ERD. |
| pencatatan_nutrisi | id_pencatatan:INT PK; id_user:INT FK; id_jadwal:INT FK; id_data_sensor:INT FK; waktu_input:DATETIME; fase_tanaman:ENUM(vegetatif,generatif); ppm_manual:INT; catatan:TEXT; created_at:DATETIME; updated_at:DATETIME | Worker physical input, backend persist; mengacu schedule/sensor/user. ERD tidak langsung memiliki id_perangkat; perlu derive melalui snapshot atau explicit device ref draft. Correction before confirm jelas, post-save CRUD Q-10. |
| parameter_validasi | id_parameter:INT PK; id_pengelola:INT FK; fase_tanaman:ENUM(vegetatif,generatif); ppm_minimum:INT; ppm_maksimum:INT; ph_minimum:FLOAT; ph_maksimum:FLOAT; suhu_air_minimum:FLOAT; suhu_air_maksimum:FLOAT; suhu_ruangan_minimum:FLOAT; suhu_ruangan_maksimum:FLOAT; kelembapan_minimum:FLOAT; kelembapan_maksimum:FLOAT; status_aktif:BOOLEAN; created_at:DATETIME; updated_at:DATETIME | Owner config; digunakan validation. Semua numeric production values TBD. Konfigurasi min/max quality/weather berbeda validity record (Q-02). |
| validasi_nutrisi | id_validasi:INT PK; id_pencatatan:INT FK; id_parameter:INT FK; status_validasi:ENUM(sudah_tervalidasi,belum_tervalidasi,terlambat_dicatat,ppm_rendah,ppm_tinggi,data_sensor_abnormal); keterangan:TEXT; waktu_validasi:DATETIME; created_at:DATETIME | Backend derived result, reference record/parameter. ERD relasi dekat one-to-one record, Class menampilkan 0..* validasi; version/history semantics Q-02/10. Pending tanpa record di flowchart belum jelas. |
| riwayat_monitoring | id_riwayat:INT PK; id_perangkat:INT FK; tanggal:DATE; total_pencatatan:INT; jumlah_valid:INT; jumlah_terlambat:INT; jumlah_abnormal:INT; created_at:DATETIME | Daily recap device; Class memiliki rekapHarian/rekapMingguan. Stored aggregate vs computed query dan rule overlapping counters belum ditentukan. |
| notifikasi | id_notifikasi:INT PK; id_user:INT FK; id_jadwal:INT FK; judul:VARCHAR; pesan:TEXT; tipe_notifikasi:ENUM(jadwal,validasi,sensor); waktu_kirim:DATETIME; status_baca:BOOLEAN; created_at:DATETIME | User receives event message; worker buzzer/LCD vs owner inbox/push channel Q-12. Optional schedule relation for sensor/validation belum specified nullable. |
| backup_lokal | id_backup:INT PK; id_perangkat:INT FK; jenis_data:ENUM(data_sensor,pencatatan_nutrisi,validasi_nutrisi); data_json:TEXT; status_sinkron:ENUM(belum_sinkron,sudah_sinkron); waktu_simpan:DATETIME; waktu_sinkron:DATETIME | Device datalogger MicroSD fallback; remote table vs logical local record unresolved. Sync time may be absent before ack logically, actual nullability needs sign-off. |

ERD memiliki **11 kotak entitas**, bukan jumlah yang disimpulkan dari narasi yang merangkum tabel. Daftar di atas memuat seluruh kotak termasuk data_sensor, notifikasi dan backup_lokal. Class Diagram memiliki class Pengguna, PekerjaGreenhouse, Pengelola, JadwalNutrisi, PencatatanNutrisi, ValidasiNutrisi, ParameterValidasi, PerangkatIoT, Sensor, DataSensor, Dashboard, RiwayatMonitoring, Notifikasi. Dashboard adalah presentation service, tidak otomatis tabel. Subclasses user tidak otomatis tabel terpisah. Enums class mencakup role, phase, six status validation, three status perangkat dan four jenis sensor. Physical sensor model dipilih menurut §1.3, bukan penambahan sensor karena enum empat parameter.

Hubungan global: users 1→many jadwal/parameter/record/notifikasi; perangkat 1→many sensor/data_sensor/riwayat/backup; jadwal→record dan notifikasi; data_sensor→record; record→validasi; parameter→validasi. Detail mandatory FK/cardinality/version belum seluruhnya konsisten sehingga physical schema membutuhkan TASK-004/007. Error/pending enums tidak berarti data dapat di-null secara otomatis. Timestamp field ERD tidak menetapkan UTC; REC-004 adalah rekomendasi baru.

## 3. Pemetaan antara current dan proposal

| Existing field/model | Proposal counterpart | Gap |
|---|---|---|
| SensorReading waterTemp/ph/roomTemp/humidity | data_sensor suhu_air/ph_larutan/suhu_ruangan/kelembapan_ruangan | Device/source IDs, DATETIME, quality/history missing |
| SensorReading timestamp display | data_sensor waktu_baca | String HH:mm/WIB bukan canonical capture datetime |
| NutrientLog id/ppm/phase/notes/timestamp | pencatatan_nutrisi id_pencatatan/ppm_manual/fase_tanaman/catatan/waktu_input | ID type/authoritative source berbeda; selected time vs timestamp mismatch; FK absent |
| recordedBy free text | id_user FK users | Tidak dapat menjadi proof author/authentication |
| status/statusLabel on log | validasi_nutrisi separate | Result conflated record; four vs six statuses; version/validatedAt absent |
| ValidationSettings schedules | jadwal_nutrisi rows | Dua jam vs collection hari/fase/active; occurrence absent |
| ppmRanges/sensorThresholds | parameter_validasi + sensor metadata | Missing owner/id/version; room/humidity thresholds absent; min/max duplikasi needs decision |
| activePhase | record phase / editor selected phase / kebun active state | Ketiganya berbeda concept; authoritative active state tidak explicit entity |
| notifications fixtures/read0 | notifikasi | No persistent event/user/schedule association |

## 4. PROPOSED application/domain contracts

Klarifikasi pengguna 10 Oktober 2026: consumer Android owner/leader-only; producer worker input IoT-only; awal satu ESP32/satu tandon dengan empat parameter sensor. Fitur jadwal pekerja hari/shift diinginkan untuk assignment berdasarkan waktu input perangkat, rancangan perilaku PRD §14/TASK-027; physical schema/DTO masih draft. Jangan memetakan otomatis nama jadwal menjadi authenticated workerId/recordedBy. Diperlukan pembedaan pekerja yang dijadwalkan vs identitas penekan tombol (tidak terverifikasi), timestamp perangkat vs receive time, serta perlakuan replay/offline/tanpa jadwal/bentrok/perubahan historis. Snapshot/version assignment adalah usulan untuk menjaga sejarah, bukan contract disahkan. Sensor memengaruhi validitas menurut pilihan B gabungan; sisa rules/threshold Q-02. Laravel/PHP REST API telah disetujui pengguna.

Draft REC-002/003/004/005/006; fields berikut sengaja tidak mengklaim menjadi approved schema. Engine telah dipilih **MySQL**; versi/environment, API/tables fisik masih keputusan Q-11. Nullability di bagian ini adalah **usulan**, berbeda dari ERD yang tidak menentukannya.

| Draft type | Proposed field names/types/nullability | Rules/unit/owner |
|---|---|---|
| Telemetry | eventId:String required; deviceId:String required; capturedAt:Instant required; receivedAt:Instant server; clockSource:enum required; waterTemperatureC:Double?; solutionPh:Double?; airTemperatureC:Double?; relativeHumidityPercent:Double?; perFieldQuality:map required | Missing reading null with quality, bukan0; finite; °C/pH/°C/%; capture device, receive server; frequency Q-11 |
| DeviceSummary | deviceId:String; name:String; location:String?; lastContactAt:Instant?; connectionState:enum; latestTelemetryId:String? | Connectivity berasal server/communication evidence; stale deadline Q-11; avoid toggle authoritative |
| ManualNutrientRecord | eventId:String; deviceId:String; workerId:String? TBD Q-06; scheduleOccurrenceId:String? TBD Q-05; capturedAt:Instant; phase:PlantPhase; manualPpm:Int; sensorSnapshotId:String?; notes:String?; receivedAt:Instant server | Stable ID, manual source, confirmed input; range/increment Q-03; timestamp meaning Q-05; relation if sensor unavailable Q-11 |
| GrowthPhaseContext | phase:PlantPhase; scopeId:String TBD kebun/device/batch; effectiveFrom:Instant? | Record phase immutable by editor; active context owner/worker responsibility Q-04; no automatic day-age formula |
| NutrientSchedule | id:String; ownerId:String server; phase:PlantPhase? TBD; localTime:LocalTime; activeDays:Set<DayOfWeek>; toleranceMinutes:Int; enabled:Boolean; zoneId:String; version:String | Recurrence draft; schedule occurrence/date separate; cardinality/early tolerance Q-05 |
| ValidationParameterSet | id:String; ownerId:String; phase:PlantPhase; ppmMin:Int; ppmMax:Int; sensorBounds:map?; version:String; effectiveFrom:Instant | Min≤max/finite structural; actual nutrient threshold TBD; sensor participation Q-02 |
| ValidationResult | id:String; recordId:String; parameterVersion:String?; scheduleVersion:String?; evaluatedAt:Instant?; primaryStatus:enum; reasons:List<reason>; authority:enum(provisional,server) | Six proposal statuses require rule sign-off; reason list and provenance new REC-005; missing config not valid |
| NotificationEvent | id:String; recipientId:String; type:enum; occurredAt:Instant; readAt:Instant?; message:String; recordId/scheduleOccurrenceId:String? | Channel/inbox/push Q-12; read0 global not sufficient |
| SyncQueueItem | eventId:String; deviceId:String; kind:enum; payload:JSON; savedAt:Instant; ackAt:Instant?; state:enum; schemaVersion:String | Local durable append, ACK server to mark sync; idempotency REC-003; no actual queue currently |
| UiState | loading/data/empty/error + refreshing/lastKnown/time/quality/saveAppliedState | Presentation only, tidak disimpan sebagai backend business status |

Timestamp recommendation REC-004: transport memakai RFC3339 dengan offset eksplisit/UTC, convert ke Instant di backend/Android; tampilkan Asia/Jakarta setelah conversion; device RTC source/correction dicatat. capture, received, evaluated dan config effective timestamps berbeda. Jangan menyimpan “08:05 WIB” sebagai waktu kanonik. Chronological queries memakai instant plus timezone scope disahkan; contoh serialisasi bukan kalender produksi. Data privacy: recordedBy display name optional bukan auth claim; backend derives identity.

## 5. PROPOSED integration messages dan operations

Keputusan pengguna 10 Oktober 2026: consumer produk adalah Android native Kotlin; React referensi UI. Producer catatan PPM tahap sekarang hanya ESP32. Scope pengerjaan direncanakan mencakup firmware, backend MySQL dan Android serta kontrak integrasinya, tetapi belum menetapkan detail DTO/API. Jangan menambahkan operasi create PPM Android. Subfitur input harian greenhouse masih future scope tanpa fields/contract; Backend Laravel/PHP telah dipilih; Kotlin untuk Android.

Rencana REST terdapat P §3.1; semua operations berikut adalah **draft semantic operations tanpa actual URL/method/deployed service**. Tidak ada known API base path, token format, host atau MQTT topic. Backend Laravel/PHP telah dipilih; versi/environment/API rinci mengikuti Q-11. DTO field naming mapping disepakati sebelum producer/client dibangun.

| Operation semantic | Producer/consumer | Draft request/response dan constraints |
|---|---|---|
| Ingest telemetry | ESP32→backend | Telemetry envelope + device authorization; server returns stable event ACK and receive time; payload limit agreed |
| Ingest manual record | Device→backend | Confirmed ManualNutrientRecord; server validates shape/access, persists and returns pending/final status separately from ACK |
| Read latest device/telemetry | Android→backend | Authorized scope/device; return latest capture and last contact, null/quality if absent |
| Read history/trends | Android→backend | Scope/date range/metric/cursor/limit; result items plus next cursor, source units; bounded queries |
| Read/update config | Owner→backend/device | Owner versioned config; structural validation; save acknowledgement and device applied acknowledgement separate |
| Login/logout/session | Owner→backend | Auth mechanism TBD; no plaintext password storage, no credentials echoed; server authorization |
| CRUD approved entity | Owner/admin→backend | Create/read/update/delete according Q-10; permission/audit and validation on server; historical edits do not silently overwrite provenance |
| Read/mark notification | Owner→backend | Per-recipient event/read state if inbox approved; not necessarily push channel |

Draft telemetry envelope shape (PROPOSED, placeholders; **tidak boleh langsung dikirim ke service**):

```json
{
  "schemaVersion": "draft-1",
  "eventId": "<stable-event-id>",
  "deviceId": "<provisioned-device-id>",
  "capturedAt": "<RFC3339 timestamp with offset>",
  "clockSource": "<agreed RTC/NTP provenance>",
  "readings": {
    "waterTemperatureC": null,
    "solutionPh": null,
    "airTemperatureC": null,
    "relativeHumidityPercent": null
  },
  "quality": "<agreed per-field quality representation>"
}
```

Manual record envelope menggunakan `eventId`, `deviceId`, `capturedAt`, `phase`, `manualPpm`, source manual/handheld, optional approved `workerId`, `scheduleOccurrenceId`, `sensorSnapshotId`, `notes`. **PPM tidak ditambahkan ke readings telemetry**. Flowchart missing input tidak otomatis membuat manual record zero/null. Idempotency key bukan Date.now-only. Request fields/types nullability mengikuti decision record, bukan placeholder template di atas.

MQTT alternatif PROPOSED/OPTIONAL: jika dipilih, semantic channels telemetry/record/config/ACK, device namespace/access control, delivery/replay/retained config behavior harus disepakati. Tidak ada broker address/topic/QoS yang diklaim existing. Dashboard dapat tetap consume backend API agar credential broker/device tidak diberikan ke owner app. Protocol choice Q-11, no requirement auto-switch MQTT.

## 6. PROPOSED sync duplicates errors dan lifecycle

- Device writes event stable ID + schema/time/payload locally sebelum remote submission bila offline. ACK harus berarti persisted, bukan hanya HTTP/socket delivered. Setelah ACK catat synced; retention/cleanup sesuai approved policy. Replay event same-ID same payload returns same persistence result; same-ID different payload conflict/quarantine. Ini REC-003, detail belum P.
- Persist raw record dan derived validation sebaiknya konsisten/transaksional; simpan parameter/schedule version/snapshot REC-005. Owner config change tidak merombak historic status diam-diam. Revalidation/validation history one-to-many Q-02/10.
- Query timeline dan device latest menggunakan capture/receive semantic yang dipilih; out-of-order telemetry/replay tidak otomatis mengganti latest dengan record lebih lama. Missing sensor snapshot boleh null hanya setelah contract approved; no made-up0.
- Draft error response: `{code, message, fieldErrors?, correlationId?, retryable?}` tanpa credentials/stack traces untuk end-user. Category shape-invalid, unauthorized, forbidden, not-found, conflict/version, payload-too-large, unavailable/storage/sensor. Exact status HTTP/codes/messages/limits TBD. Field validation user-recoverable; network unavailable → retry/last-known; permission errors tidak blind-retry; no config/sensor → pending/error sesuai rule.
- Android cache jika dipilih adalah read replica, backend authoritative. Config writes offline queue tidak approved. Token/session storage dan encrypted/secure store dipilih berdasarkan mechanism, bukan `.env` dalam APK. Backend server/device secret provisioning berbeda owner session.
- Retention/delete/correction rules UNCONFIRMED. CRUD SOP diwujudkan pada entitas yang disepakati, tidak otomatis semua sensor/audit events editable/deletable oleh pekerja.

## 7. Contract gates dan pengujian

Update U 10 Oktober 2026: owner phase aktif dan worker submitted phase terpisah, mismatch warning-only tanpa overwrite/reject otomatis. Contract perlu source/effective timestamp dan phase/config version pada record; pastikan payload phase bukan akses mengubah fase tandon. Semua konfigurasi agronomis/jadwal/toleransi kosong sampai owner mengisi, tanpa seed produksi; incomplete configuration tidak valid. Validation B mempertahankan all reasons. Izin implementasi/testing/debugging scope Android/backend/firmware telah diberikan; physical DTO/API/DDL boleh dirancang dan dicatat, namun agronomic values/behavior unresolved tidak ditebak. Tidak ada schema/service/test baru yang sudah diverifikasi dari update ini.

**Keputusan terbaru mengatasi ketidakpastian sebelumnya:** backend Laravel/PHP REST API + MySQL disetujui pengguna; validasi **B gabungan** dipilih tahap awal. Bukan bukti service existing. Jadwal pekerja dirancang sesuai PRD §14/TASK-027, sebagai assignment, bukan authenticated worker identity. Draft extension (PROPOSED sampai physical schema/DTO TASK-004): WorkerProfile(id,displayName,active); WorkerShiftVersion(id,workerId,tankScopeId,zoneId,activeDays,startLocalTime,endLocalTime,effectiveFrom,effectiveUntil?,version); ShiftOccurrence(id,shiftVersionId,startAt,endAt); WorkerAssignment(recordId,occurrenceId?,scheduledWorkerId?,workerNameSnapshot?,shiftSnapshot?,status,assignedAt). ID/nullability/storage final belum disahkan. Rentang [startAt,endAt), rollover, non-overlap, source event time dan historical version retention mengikuti PRD §14. Server menentukan assignment; payload producer tidak menetapkan actor authorization. ManualNutrientRecord membutuhkan confirmedAt/source clock terdefinisi; workerId draft tidak otomatis identity terverifikasi. Event navigasi tombol tidak menciptakan nutrient record.

Validation B menyimpan hasil waktu/fase/PPM/sensor dan semua reasons agar status gabungan dapat dijelaskan; tidak valid bila pemeriksaan sensor terkait gagal. Missing/failed/stale sensor/config tidak boleh menjadi valid, tetapi final pending/error category masih TASK-003. Rule version/provenance dipertahankan saat perubahan setelah testing; tidak merevalidasi history diam-diam. Screenshot Stitch bukan source nilai default.

CONFIRMED: engine **MySQL**, sumber pengguna 10 Oktober 2026. UNCONFIRMED: versi/environment MySQL, framework backend, actual endpoint/base/DDL/auth/session/device trust, worker identity, number devices/kebun/batch, sensor vs validation policy, PPM bounds/precision, phase ownership, schedule recurrence/timezone/alarm/pending, pinout, actual payload limit (10KB rekomendasi SOP), nullability FK when offline, event ID format, clock tolerance, ACK/retry/retention, CRUD rights/audit, notification channel, timestamp conventions. Referensi Q-01–14 PRD; jangan schedule ini sebagai coding-ready tanpa keputusan relevan TASK-003/004.

Contract acceptance setelah sign-off: deserialize unknown/missing/invalid enum/time/number; lossless phase/manualPPM/unit/time round-trip; authorized device/owner separation; pagination stable; duplicate same payload no new record; collision conflict; old config/precondition conflict; config ACK vs apply distinct; sensor missing/late, queue restart/network outage, history corrections audited. Semua runnable command tergantung backend/Android toolchain aktual (IMPLEMENTATION_PLAN). Tidak ada API test yang sudah lulus saat audit.
