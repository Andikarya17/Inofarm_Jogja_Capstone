# REST API Inofarm v1

Implementasi aktual: `backend/routes/api.php`. Base `/api/v1/`; Content-Type/Accept `application/json`, Bearer token. APK hanya mengakses owner endpoints; device token hanya producer endpoints. MySQL tidak diakses klien.

## Endpoint

| Method/path | Aktor | Perilaku |
|---|---|---|
| POST login | Publik | `{email,password}` → `{token,expiresAt,user:{id,tank_id,name,email,role}}`. Role/tandon ditentukan server, token owner/leader kedaluwarsa satu hari. Stable IDs dan expiry memberi scope cache Android; bukan credential database. |
| POST logout | Owner | Revoke token saat ini. |
| GET dashboard | Owner | `{device,connectionState,telemetry,latestRecord,latestTelemetryReceipt,latestRecordReceipt,activePhase,configVersion,serverTime}`. Nilai dapat null. |
| GET config | Owner/device | `{config,activePhase,timezone}`; dua versi terpisah, initial null. |
| PUT config | Owner | `{baseVersion:null atau id,config:{...}}` → `{config:versiBaru}`. |
| PUT active-phase | Owner | `{baseVersion:null atau id,phase:vegetatif/generatif}` → `{activePhase:versiBaru}`. Editor PPM tidak memanggil endpoint ini. |
| GET history / telemetry | Owner | Cursor envelope `{data,next_cursor,...}`; limit 1–100, default 25, cursor opaque. |
| GET history/export | Owner | CSV, filter sama dengan history, maksimum 1000 records; lebih banyak 422. |
| GET workers / shifts | Owner | Page envelope `{data,current_page,last_page,...}`, page size 100. Android membaca seluruh page. Shift list hanya versi aktif. |
| POST workers | Owner | `{name}` → `{id,version,name,active}`. |
| PUT workers/{id} | Owner | `{baseVersion,name,active}`; append versi nama/status. |
| DELETE workers/{id} | Owner | `{baseVersion}`; deaktivasi dan tutup shift aktif, tanpa menghapus snapshot lama. |
| POST shifts / PUT shifts/{key} | Owner | Body shift di bawah; append versi, tutup versi sebelumnya. |
| DELETE shifts/{key} | Owner | `{baseVersion}`; tutup versi aktif, 204. |
| POST device/events/record | Device | Durable manual record, ACK 201 baru / 200 replay identik. |
| POST device/events/telemetry | Device | Durable monitoring event; tidak menghasilkan catatan nutrisi. |
| GET device/config | Device | Konfigurasi/fase server saat ini; sama dengan GET config. |
| POST device/config-applied | Device | `{version:id}` setelah cache durable; ACK `{appliedVersion}` terpisah dari penyimpanan konfigurasi server. |

Login salah maksimal lima percobaan per email/IP sebelum rate limit; route login 20/min, scoped API 120/min. Request maksimum 16 KiB; pilihan teknis, bukan batas SOP 10KB atau agronomis. Errors standar `{message,errors?}`: 401 autentikasi, 403 identitas/scope, 404 resource tidak terlihat, 409 optimistic conflict/event ID conflict, 413 payload, 422 form, 429 rate limit. Klien tidak mengklaim berhasil jika respons gagal.

## Konfigurasi produksi kosong

Belum pernah diisi → `config:null`, `activePhase:null`. Owner dapat menyimpan konfigurasi parsial:

```json
{
  "baseVersion": null,
  "config": {
    "timezone": "Asia/Jakarta",
    "schedules": [],
    "earlyMinutes": null,
    "lateMinutes": null,
    "sensorMaxAgeSeconds": null,
    "deviceStaleSeconds": null,
    "telemetryIntervalSeconds": null,
    "alarmSeconds": null,
    "ppmRanges": {},
    "sensorBounds": {}
  }
}
```

`schedules` maksimal 14 rows `{days:[ISO weekday 1..7],time:"HH:mm"}`. Hari harus unik dalam setiap row; hari sama antar-row dengan waktu berbeda diterima. Owner mengisi sendiri; tidak ada jam produksi bawaan. `ppmRanges` memakai key vegetatif/generatif, `sensorBounds` empat key metrik di bawah; setiap key boleh belum ada/null atau `{min:number,max:number}`, finite, min≤max. Batas inklusif merupakan mekanisme evaluasi B-1; angka tidak disediakan sistem. Config body/snapshot menjaga JSON map kosong sebagai object `{}`.

Toleransi awal/akhir nullable 0..1440 menit; usia snapshot nullable 0..604800 detik; batas kontak nullable 1..604800 detik; cadence nullable 2..86400 detik; alarm nullable 1..3600 detik. Angka tersebut batas teknis form/driver, bukan rekomendasi agronomis. Config baru berlaku sejak server menyimpan, tanpa backdate. `baseVersion` harus versi terakhir masing-masing resource; 409 meminta muat ulang. Config dan fase memakai sequence berbeda. Devices melaporkan cache applied terpisah; server save tidak membuktikan pengingat perangkat berfungsi.

## Shift pekerja

Body `{baseVersion:null atau versiId,workerId,days:[1..7],startTime:"HH:mm",endTime:"HH:mm",startDate:"YYYY-MM-DD",endDate:null atau tanggal}`. `shift_key` UUID stabil, `id` versi. Days/date mengacu hari **mulai** shift dalam WIB; endDate inklusif terhadap hari mulai. EndTime≤StartTime berarti lintas malam, tetapi jam sama ditolak. Interval [start,end) memastikan batas tepat masuk shift berikutnya. Overlap ditolak 422 dalam transaction/lock tandon. Worker harus aktif dan berasal tandon actor.

Update/deaktivasi berlaku sejak waktu server, bukan retroaktif. Assignment source confirmedAt memilih shift/name version historis, termasuk replay offline setelah rename/deaktivasi. Event menyimpan `{status:scheduled,workerId,workerNameSnapshot,shiftVersionId,shiftSnapshot,startAt,endAt,assignedAt}`. Alternatif `unscheduled`, `time_unknown`, `conflict` tanpa menebak nama. Label aplikasi **Pekerja terjadwal**; bukan verifikasi penekan tombol. Jadwal pekerja terpisah dari jadwal pemberian nutrisi.

## Event producer

Record transport:

```json
{
  "schemaVersion": "1",
  "eventId": "UUID-v4-stabil-sebelum-queue",
  "deviceId": "UUID-device-provisioning",
  "clockSource": "rtc",
  "clockStatus": "trusted",
  "confirmedAt": null,
  "measuredAt": null,
  "phase": "vegetatif",
  "manualPpm": 0,
  "sensorSnapshot": {
    "capturedAt": null,
    "readings": {"waterTemperatureC":null,"solutionPh":null,"airTemperatureC":null,"relativeHumidityPercent":null},
    "quality": {"waterTemperatureC":"missing","solutionPh":"missing","airTemperatureC":"missing","relativeHumidityPercent":"missing"}
  }
}
```

UUID placeholders harus diganti UUID valid. PPM 0 di contoh hanya bentuk JSON, **bukan nilai default produksi**. Firmware menunggu input handheld/review. Telemetry memakai field umum schemaVersion/eventId/deviceId/clockSource/clockStatus/sensorSnapshot tanpa field record. Optional notes maksimum 1000 karakter. Field tak dikenal, misalnya workerId/fake validity, ditolak. DeviceId wajib cocok token.

Waktu berupa RFC3339 offset/Z, maksimal enam digit pecahan atau null jika tidak diketahui; validasi kalender berlaku. Source rtc/ntp/unknown, status trusted/invalid. Firmware saat ini RTC saja. `measuredAt` waktu pengukuran handheld aktual; tetap null bila tidak diketahui, tidak disamakan dengan navigasi/konfirmasi. `confirmedAt` saat OK simpan; `capturedAt` saat snapshot empat sensor. Unit °C, pH, °C, %. Quality ok/missing/failed/uncalibrated; ok memerlukan angka finite, selain ok wajib null. manualPpm 0..999999999, maksimal tiga desimal adalah kapasitas penyimpanan teknis; UI ESP32 enam digit bulat.

Server `clock_trusted` berarti clock declaration bukan unknown/invalid, source tersedia dan tidak berada di masa depan saat receive. Ini bukan bukti RTC fisik akurat. Raw waktu tetap body; indexed `source_at` null bila clock tidak dipercaya. Server tidak mengganti waktu source dengan receive. Timestamp lama boleh replay. Initial konfigurasi tidak berlaku retroaktif pada catatan yang lebih lama.

## ACK, history, dan validasi B

ACK `{persisted:true,duplicate:false/true,event:{...}}` hanya setelah transaction sukses. Kombinasi deviceId/eventId unik; canonical validated payload identik mengembalikan event/snapshot yang sama; ID sama dengan isi berbeda 409. Firmware menyimpan event JSON durable sebelum kirim dan ACK cocok ke SD sebelum menandai selesai; file tidak dihapus otomatis. Source time dipertahankan. Pemeriksaan power loss SD masih NOT_VERIFIABLE.

Event response memakai snake_case database envelope: id, event_id, device_id, tank_id, kind, body, source_at, clock_trusted, received_at, phase, manual_ppm, config_version_id, phase_version_id, validation_result, assignment. Token/hash tidak diekspos. Body menjaga field producer asli. Source/received/evaluation dipisah; UTC transport disajikan WIB oleh Android.

`validation_result` record menyimpan status, checks (check/result/code/message), evaluatedAt, authority=server, ruleVersion=B-1, configSnapshot, ownerPhaseSnapshot. Telemetry validation_result/assignment null. B-1 memeriksa clock, fase owner, PPM fase pekerja, occurrence jadwal/toleransi, waktu/usia snapshot dan quality/bounds seluruh sensor. Fase mismatch warning memengaruhi keseluruhan `issues`, tanpa menolak atau menimpa input. Jika ada incomplete, status utama incomplete; jika tidak, issue/warning → issues; seluruh checks termasuk pass tetap ada. Tidak ada agronomic values yang diisi otomatis.

Window jadwal yang cocok mana pun dapat lulus (toleransi asimetris tidak boleh memilih occurrence terdekat yang salah). Bila tidak cocok, occurrence terdekat memberi alasan awal/terlambat; tidak ada occurrence sekitar hari sumber memberi alasan tersendiri. Sensor gagal/missing/uncalibrated, waktu invalid, data setelah konfirmasi/stale, ambang belum diisi tidak dianggap normal. Rename/config baru tidak merombak validasi/assignment event lama; tidak ada revalidation otomatis.

History/telemetry sorted id descending (sequence penerimaan immutable, bukan asumsi source timestamp valid); cursor stabil juga untuk clock null. Filter from/to tanggal WIB inklusif (server query [awal hari, awal hari berikutnya)), status valid/issues/incomplete untuk record. Clock unknown tidak cocok date filter karena tidak menebak tanggal. CSV menyertakan sumber/clock/measurement/receive/evaluation, kedua fase, PPM, status, pekerja, versi dan semua checks; text formula-start di-escape. Android memakai Storage Access Framework untuk memilih file. Tren sensor memakai maksimal 100 event dimuat, PPM halaman history yang dimuat, gap missing tidak disambung.

## Persistence dan security boundaries

Tabel MySQL: tanks, users, devices, personal_access_tokens, config_versions, phase_versions, workers, worker_versions, shift_versions, events; cache/jobs skeleton hanya infrastruktur. Events menyimpan JSON snapshot/provenance, DECIMAL PPM, UUID unique, index tandon/kind/source/id dan FK versi. Password hashed Laravel; token owner/device hashed server, token Android AES-GCM/Keystore. Provisioning CLI lokal tidak membuat konfigurasi agronomis. Koreksi/hapus record historis belum disahkan; API tidak menyediakan overwrite/delete event. Pentest dan hardware acceptance tetap gate handover.


## Dashboard freshness dan cache baca Android

`telemetry` dan `latestRecord` memilih event dengan clock_trusted=true dan source_at tersedia, urut waktu sumber lalu ID. Replay lama tidak menggantikan bacaan sumber yang lebih baru. `latestTelemetryReceipt` dan `latestRecordReceipt` memilih ID penerimaan terbaru termasuk clock-invalid, dengan envelope event yang sama. Receipt baru bukan bukti capture baru. `device.last_seen_at` adalah kontak backend; connectionState memakai deviceStaleSeconds owner jika tersedia. Missing batas tetap unknown. Waktu capture/receipt/serverTime/fetch dan quality ditampilkan terpisah; usia pada serverTime bukan TTL agronomis.

Android refresh saat resume dan setiap 30 detik selama dashboard aktif; pending pemeriksaan menandai kontak last-known. Cache schema 1 terenkripsi dan scoped stable server/account/tank, maksimum 500 record deduplikasi ID, snapshot resource dan file maksimum 2 MiB. Jika file terlalu besar atau write gagal, cache lama tetap dipakai dengan provenance lama. Pagination cursor cache null. Pembukaan mencoba server; cache bukan data terbaru dan validasi historis tidak dihitung ulang. Logout/401/known expiry menghapus cache; cache hanya dapat dibaca selama sesi dengan expiry diketahui belum berakhir. Tidak ada API/offline mutation tambahan.

Firmware HTTP bench memerlukan gate eksplisit dan IPv4 privat kanonis. Jaringan pada worker terpisah dari loop tombol/alarm. Event ditolak/rusak diberi marker `.blocked` dan payload dipertahankan; event independen tetap berjalan. ACK/blocked marker tidak menghapus file event; hardware SD durability belum terverifikasi.


## Klarifikasi consumer Android — 13 Oktober 2026

Bagian cache schema1 di atas adalah implementasi historis; Android sekarang schema2 per-resource dengan total2MiB termasuk staging AtomicFile, maksimal500 history, server/account/tank scope, fetchedAt/filter/reasons/clock tetap, tanpa cursor cache. Schema1 diinvalidasi tanpa menghapus sesi; offline sesaat setelah transisi bisa tidak mempunyai snapshot. Request server sukses tetap sukses bila cache gagal/oversize. Tidak ada endpoint atau rule agronomis berubah.

GET history existing mengembalikan prev_cursor selain next_cursor karena ReadController langsung memakai Laravel CursorPaginator; vendor CursorPaginator::toArray diperiksa. Android meneruskan cursor opaque tersebut pada parameter cursor existing untuk previous/next, maksimal4×25 row per jendela. Cache tidak membuat cursor, export tetap seluruh applied filter dengan limit server existing. Workers/shifts existing paginate100; Android mempertahankan semua halaman untuk konsistensi editor, dengan rendering lazy. Refresh resume/30detik dashboard RESUMED tetap.
