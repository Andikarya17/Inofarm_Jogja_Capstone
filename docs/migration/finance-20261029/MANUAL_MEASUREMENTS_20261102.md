# Pengukuran manual dan pelepasan IoT — 2 November 2026

Permintaan pengguna terbaru menggantikan coexistence legacy dalam FIN17. Scope aman adalah perubahan source, build dan pengujian terisolasi; tidak deploy/drop tabel/uninstall/clear data. Baseline main `2269efa`, bersih dan sama dengan GitHub main saat fetch. Alias remote `github` ditambahkan ke URL origin yang sama tanpa mengganti origin.

## Inventaris dan acceptance

| Batas | Komponen | Keputusan |
|---|---|---|
| Khusus IoT | Android owner/viewmodel/data/UI sensor/config; backend v1 controllers, device/owner middleware, provisioning/validator; firmware/PlatformIO; React monitoring | Hapus setelah pemakai finance dipisahkan dan checks tersedia |
| Bersama finance | Tema, validasi URL, MainActivity, User/token, MySQL guard, migrations, backup inventory | Pertahankan atau pisahkan seminimal mungkin |
| Histori | Tabel tank/device/event/config, migrations 1–14, identity map schema8, docs/archive + manifest/provenance | Pertahankan tanpa mengubah arti/data; tidak konversi menjadi aktivitas manusia |
| Obsolete tooling | Build/package firmware, runner owner, prototipe npm/Vite | Verifikasi referensi lalu lepaskan dari jalur produk |

AC: activity `measurement` tanpa bahan, EC mS/cm dan PPM ppm lengkap untuk submit, pH opsional tetap; nullable EC historis; actor dari sesi; ledger/audit/receipt/feed dan local transaction existing. Dashboard tepat dua kartu dengan pasangan dari satu record scoped, ordered occurred_at lalu UUID descending, kalender WIB; future/draft/cancel/rejected/conflict/epoch/scope hold dikecualikan. Hari ini → kemarin → sebelumnya, missing bukan nol. Queued lengkap dilabel belum tersinkron. Cache tidak bergantung satu halaman riwayat. Koreksi/pembatalan/revoke/logout mengubah tampilan.

AC runtime: finance default tanpa flag, identitas debug/profile `.finance` tetap, release identity lama tetap dan distribusi/signing masih gate; SQLite9/draf/outbox tidak dihapus. API v2 tetap, v1 tidak terdaftar, source produk tidak memerlukan npm/firmware. Regresi HSS/HST/keuangan/offline/lifecycle wajib dilaporkan aktual.

Baseline checks: contract runner awal terhalang jsonschema belum terpasang. Context checker gagal satu link gambar historis `docs/implementation/UI_REDESIGN.md` ke output yang tidak ada; arsip tidak diubah. PHP awal8.2 tidak memenuhi composer; vendor belum tersedia. JDK25 ditemukan di `.jdks`, Gradle wrapper tersedia. Setup prasyarat lokal dilakukan dalam `.tools` yang diabaikan Git. Hasil final dicatat pada evidence terpisah.

## Batas pelepasan source yang telah diverifikasi

`codex/retire-iot-runtime` bergantung `0d960db`.103file dedicated dilepas awal, ditambah README/certificate TLS test owner dan2asset atribusi Haze yang tak lagi dibundel (total107). Seluruh pemakai `data.ApiClient` finance diganti validator `core/network/ApiBaseUrl`; tema/token/font dipindah ke `core/design/InofarmTheme` tanpa dependensi Haze/policy efek glass owner. MainActivity hanya FinanceRoot; gate FINANCE_APP dan owner export/viewmodel dilepas, JobScheduler tetap1601. Navigation-compose/Haze khusus owner tidak dipakai finance dan dilepas.

Controller v1 enam buah, middleware DeviceAuth/OwnerScope, service ingest/config/validator/shift, provision device/owner/demo dan model Eloquent khusus v1 tidak mempunyai pemakai auth v2/audit/recovery/migration. Setelah dilepas, `User`/Sanctum tetap, termasuk kolom historis `tank_id`; recovery memakai inventory tabel/kolom/FK dinamis dan immutable v2 ledger, tidak membutuhkan model lama. Semua migration1–14/map schema8/tabel legacy utuh. Tidak ada SQL drop/convert legacy dalam task ini.

Tes yang dilepas hanya implementation khusus route/UI/provisioning v1. Assertion v2 bahwa `/api/v1/dashboard` menolak token diperbarui dari403 menjadi404 karena route tidak terdaftar; owner-only token tetap ditolak finance. Tes penolakan seluruh registrasi v1/middleware/command ditambahkan. Dua file JVM legacy10kasus dilepas dan2tes transport URL finance ditambahkan; semua tes finance sebelumnya tetap. Test key publik TLS owner tidak lagi mempunyai consumer, pengecualian gitignore key itu dilepas.

Build normal code2/min29/debug-profile `.finance`, release `com.inofarm.jogja`; provider/SQLite9/vault/JobScheduler tetap. `install -r` bukan uninstall/clear. README/Cara Instalasi/FIN17/runner/package hanya Android+Laravel/MySQL; scripts tidak memerlukan flag, firmware atau npm. Prototipe React/config/Gemini example env root khusus prototipe dilepas; backend `.env.example` tetap.

Pengecualian istilah IoT: docs/archive dan histori evidence/proposal, migration/tabel/kolom historis, User tank_id, tes penolakan v1/legacy ability, serta ignore secret file dari checkout lama. Itu bukan runtime sensor/perangkat. Source yang telah dilepas dapat ditinjau lewat Git, tanpa salinan arsip besar.
