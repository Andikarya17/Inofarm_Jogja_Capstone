# Data contracts target v2

Status **DESIGN keseluruhan**; subset core/master/reference/activity sudah executable dan diuji, lihat [status](IMPLEMENTATION_STATUS.md) dan [kontrak v2](../../contracts/v2/README.md). Sumber proposal §2.2, PDF19–50. [API_V2](../migration/finance-20261029/API_V2.md) adalah peta rancangan FIN00. Penamaan teknis bukan business approval; label UI Indonesia. Bekukan kontrak tiap modul sebelum implementasi lintas layer.

## 1. Konvensi umum

UUID objek stabil (CHAR(36) kandidat kompatibel); users legacy boleh mempertahankan bigint internal dengan mapping UUID publik eksplisit. Version integer naik monoton. Waktu kejadian ISO8601 dengan offset; server simpan UTC + metadata zona Asia/Jakarta. Tanggal agronomis memakai DATE lokal, bukan durasi jam/24. received_at berbeda dari occurred_at. actor_id ditetapkan server. Foreign key dan validasi lintas parent wajib.

Uang JSON string decimal, MySQL DECIMAL, Kotlin BigDecimal; jangan Double/Float untuk akumulasi uang. Q10: pengguna menyetujui IDR, DECIMAL(20,2) nominal, DECIMAL(20,6) kuantitas/harga dan HALF_UP pada total transaksi. Batas/skala input di contracts/v2/README.md; intermediate/rasio/solver per modul dibekukan sebelum digunakan. Jangan silently truncate overflow. Angka kosong null, bukan0; format Rupiah terpisah dari wire. Domain transaksi uang belum tersedia pada FIN06.

Master inaktif masih dapat dirujuk histori; semua koreksi/batal mempunyai version, reason, actor, server timestamp dan before/after atau change set. Status domain terpisah dari status jaringan/UI.

## 2. Entitas dan invariants

| Entitas/tabel usulan | Field utama | Relasi dan constraint |
|---|---|---|
| users / user profiles | id, public_id, name, email, password_hash, role, active, version | Role pekerja/pengelola; jangan percaya role payload |
| greenhouse_assignments | id, user_id, greenhouse_id, effective_from/until, active, version | Scope diperiksa ulang saat sync; histori penugasan tersimpan |
| greenhouses | id, name, active, version | Bukan rename otomatis tanks |
| varieties, materials, units, grades, expense_categories | id, code/name, active, version; unit dimension | Master scoped, tidak hard-delete yang direferensikan |
| seasons | id, greenhouse_id, name, start/end, status, version | open/closed; reopen alasan/audit |
| planting_groups | id, season_id, variety_id, population, sowing_date, transplant_date, version | transplant>=sowing; context derivable dan konsisten |
| stage_definitions / stage observations | id, group/activity, actual_stage, version | Aktual dipilih pengguna; bukan auto umur |
| schedule_versions | id, schedule_id, group_id, activity_type, planned_at, effective range, version | Satu jadwal→banyak realisasi; completion rule terpisah |
| target_versions | id, group_id, activity_type, parameter, target/range/tolerance, unit, effective range, version | Ambiguitas overlap harus ditolak atau precedence disahkan; tidak memilih sembarang |
| activities | id, group_id, schedule_id nullable, type, occurred_at, recorded_by, performed_by, ppm/ph nullable, stage, notes, work_state, completeness, record_state, version | creator dari sesi; validasi menurut jenis; skipped_reason wajib |
| activity_materials | id, activity_id, material_id, quantity/dose, unit_id | Multi-bahan; quantity>=0; konversi satuan hanya aturan sah |
| activity_reference_snapshots | id, activity_id, activity_version, target_version_ids, reference_dates, comparator_version, checks | Immutable, seluruh alasan; revisi eksplisit |
| harvests / harvest_items | id, group_id, occurred_at, recorder, version; harvest_id, grade_id, fruit_count, weight_kg | Sumber saldo penjualan; bobot/jumlah nonnegatif dan kelengkapan tervalidasi |
| expenses | id, paid_at, category_id, amount, payment_kind, notes, version | Pembayaran aktual; operating vs asset acquisition jelas |
| cost_allocations | id, expense_id, season_id, amount, cost_class, allocation_basis, version | Sum alokasi aktif<=source; FC/VC eksplisit |
| assets | id, acquisition_expense_id nullable, cost, residual_value, useful_life_years, in_service_date, version | life>0; 0<=residual<=cost; link pembayaran mencegah duplikasi |
| depreciation_allocations | id, asset_id, season_id, period, usage_share, amount, method_version, basis, version | Batas cumulative<=cost-residual; penggunaan sama tidak dua kali |
| sales / sale_items | id, season_id, paid_at, status, version; sale_id, harvest_item_id, quantity_kg, price_per_kg | Lunas; harvest same season; sum sold net active<=harvest available |
| investment_scenarios / cash_flows | id, greenhouse_id, base_date, initial_outlay, horizon_years, rate, convention, version; scenario_id, year, amount, source_kind, source_ref, assumptions | Unique(scenario,year); tahun1..n lengkap eksplisit; year0 hanya initial_outlay |
| report_snapshots | id, scope, filters, source_versions, input, formula_version, assumptions, result, completeness, created_by, created_at | Immutable; snapshot yang sama untuk UI/PDF/CSV |
| attachments | id, owning_object, uploader, private_path, mime, bytes, checksum, upload_state | Akses mengikuti objek/role; path tidak dipublikasikan |
| audit_logs | id, entity_type/id, old/new version, actor, action, reason, changes, server_at | Append-only; isi sensitif dibatasi |
| sync_operations | operation_id, actor_id, entity_id, action, base_version, request_hash, result, epoch | Unique actor+operation; mutation/result/audit atomic |
| change_feed | sequence, entity/id, version, scope, tombstone, committed_at | Pull cursor stable; tidak bocorkan data tak berwenang |
| backup_runs / restore_runs | id, manifest, checksum, status, actor, timestamps, verification, epoch | Akses terbatas; restore bukan sukses sebelum verify |

Index minimal: assignment(user,greenhouse,active), season(greenhouse,status), group(season), activity(group,occurred_at,id), harvest(group,occurred_at), allocation(expense,season), sale(season,paid_at), sale_item(harvest_item), audit(entity,id,version), operation(actor,operation_id), change_feed(sequence,scope). FK restrict historis; tidak cascade-delete catatan keuangan. Validasi scope dan agregat lintas row tidak cukup dengan CHECK, perlu transaksi/lock dan test concurrency MySQL.

## 3. Sinkronisasi

Envelope usulan: `operation_id`, `entity_id`, `entity_type`, `action=create|update|cancel`, `base_version`, `schema_version`, `sync_epoch`, `payload`. UUID operation dibuat sekali saat enqueue; retry byte/canonical equivalent memakai ID yang sama. Koreksi baru memakai ID baru dengan versi terbaru. Identitas actor diperoleh server.

Local transaction menyimpan entitas/draft+outbox. Response accepted berisi operation_id/entity_id/version/committed_at dan hasil authoritative. ACK hanya boleh mengubah operasi yang cocok; tidak menghapus koreksi baru yang belum dikirim. Unique key serta hash payload mencegah duplikasi dan ID sama isi berbeda. Replay setelah hak dicabut tidak boleh membocorkan result; authorization selalu diperiksa.

Conflict 409 menyediakan current_version dan data yang berhak dibaca; UI membandingkan lokal/server, pilihan review menghasilkan operasi baru. Tidak merge saldo uang otomatis. 401 meminta login ulang akun yang sama, 403/revoked menghentikan antrean terkait, 422 menampilkan errors, 429/5xx/network retry terukur dengan backoff tanpa mengganti ID. Semua status dan error codes dibakukan FIN-01.

Pull paginated cursor dengan upsert version-aware dan tombstones. Account/server scope terikat; pergantian akun tidak mengirim draft orang lain. Setelah restore epoch berubah: seluruh operasi lama ditahan; rekonsiliasi receipts/versions sebelum retry, bukan clear queue. Retensi receipts harus melampaui jangka offline yang didukung; kebijakannya belum diputuskan.

## 4. Rumus evaluasi musim (proposal Tabel 2.7)

- D=(A-S)/n, n>0, 0<=S<=A. Alokasi berdasarkan waktu/proporsi yang disepakati; D×proporsi/jumlah musim setara hanya bila asumsi musim setara berlaku. Jangan menganggap semua musim setara secara default.
- TC=FC+VC; TR=sum(q_j×p_j), hanya penjualan lunas aktif; L=TR-TC.
- R/C=TR/TC dan B/C sederhana=L/TC=R/C-1, hanya TC>0. Label B/C bukan discounted benefit-cost ratio.
- Q=sum kg terjual; P=TR/Q untuk Q>0.
- BEP kuantitas biaya tercatat=TC/P untuk P>0. Asumsi total biaya tetap pada skenario dibandingkan harus tampil.
- BEP harga=TC/Q untuk Q>0. Bukan rumus margin kontribusi FC/(P-v).

Setiap indikator punya `{value, unit, status, reasons, formula, inputs, assumptions}`. Missing/invalid tidak menjadi nol/Infinity/NaN. Nilai TC/TR nol sah bila memang data nol yang diketahui; tidak menyamakan missing records dengan bukti nihil transaksi. Hasil sementara menyebut kelengkapan dan sumber. Catatan batal/draf tidak diagregasi.

Fixture proposal: FC5.000.000, VC3.000.000, TC8.000.000, Q400, P30.000, TR12.000.000, L4.000.000, R/C1,5, B/C0,5, BEPqty266,666..., BEPharga20.000 (yang terakhir diturunkan dari rumus, bukan baris tabel contoh). Fixture bukan seed produksi.

## 5. Investasi (proposal PDF45–47)

NPV=-I0+sum(CFt/(1+r)^t), t1..n. Horizon integer positif, tahun tidak ganda/hilang, finite, 1+r>0. Tahun relatif tanggal acuan, bukan langsung musim. Nilai aktual/proyeksi dan asumsi sumber dicatat; depresiasi tidak dibebankan sebagai kas; nilai sisa hanya jika diasumsikan terealisasi pada akhir horizon dan tidak dihitung dua kali.

IRR adalah akar NPV=0. Rancangan awal hanya conventional flows: initial outflow positif, CF setelahnya>=0, setidaknya satu positif. Di luar cakupan/tidak konvergen → unavailable dengan alasan; NPV tidak ikut dinonaktifkan jika valid. Algoritme solver/toleransi/iterasi/bracket perlu contract dan test; jangan memaksakan 0%.

PP/DPP memerlukan I0>0. PP memakai cumulative cash; DPP cumulative discounted cash. Interpolasi: tahun penuh k + sisa pada akhir k / cash tahun pemulihan (discounted untuk DPP). Alternatif konvensi akhir tahun harus berlabel; pilihan final belum disepakati. Belum kembali sampai horizon → status khusus, bukan 0. Kas negatif setelah recovery diberi warning.

Fixture: I0=100.000.000; CF1=CF2=60.000.000; r=0,10; n=2; NPV≈4.132.231,40495868; IRR≈13,0662386292%; PP=1,666666...; DPP=1,916666... dengan interpolasi. Maksimal deviasi nominal Rp1; usulan rasio0,0001, IRR0,0001 poin persentase, PP/DPP0,0001 tahun. Nilai tabel dibulatkan hanya untuk presentasi; test memakai oracle lebih presisi.

## 6. Migrasi data

Tidak ada equivalence tank→GH, phase→stadia, shift assignment→authenticated worker, event→activity, telemetry→manual measure tanpa pemetaan eksplisit. Simpan sumber legacy ID dan origin bila importer kelak disetujui. Jangan menjadikan event demo transaksi aktual. FIN00 belum membuat source; setelah FIN06, migration/core OpenAPI/local DB tersedia sesuai status. Schema seluruh domain finance/report/recovery dan cutover belum selesai.
