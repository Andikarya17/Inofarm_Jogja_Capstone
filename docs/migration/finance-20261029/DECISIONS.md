# Decision register target baru

Status pelaksanaan: [IMPLEMENTATION_STATUS](../../context/IMPLEMENTATION_STATUS.md). Q10 angka disetujui pengguna saat pelaksanaan (IDR, nominal20,2, kuantitas/harga20,6, HALF_UP total); solver tetap belum disahkan. Pengguna juga mengoreksi publikasi ke remote GitHub `github`; otorisasi ini mengatasi tujuan GitLab operasional sebelumnya, bukan konfirmasi gate pengumpulan akademik. Baris audit FIN00 di bawah dipertahankan sebagai histori, bukan status executable terbaru.

## Keputusan aktif

| ID | Keputusan | Dasar/status |
|---|---|---|
| D01 | Proposal keuangan 60 halaman menjadi baseline aktif | Instruksi pengguna29 Oktober 2026 |
| D02 | Kotlin + Jetpack Compose, Laravel REST, MySQL | Pengguna eksplisit; tidak ganti stack |
| D03 | Operasional dan keuangan tanpa IoT, dua peran | P §1.3, KF01–14; mengatasi scope owner-only/ESP32 lama |
| D04 | Task saat ini audit, context pack dan master prompt | Deliverable turn ini; implementasi executable disusun untuk dimulai lewat prompt |
| D05 | Pertahankan source/data legacy; tidak auto-convert event | Keputusan engineering untuk menjaga provenance dan data pengguna |
| D06 | Namespace v2, schema additive, transaksi+audit+receipt | Rancangan teknik untuk dibekukan FIN01, bukan kutipan endpoint proposal |
| D07 | Test backend hanya MySQL inofarm_test | Instruksi proyek dan P §3.4; guard tidak dinonaktifkan |
| D08 | Setiap fitur/bug/config/doc task branch codex/ terpisah | Preferensi pengguna; branch task sekarang codex/finance-proposal-context |
| D09 | Penjualan lunas sebagai model awal; proyeksi terpisah aktual | Proposal; kebutuhan konfirmasi mitra tidak dihapus |
| D10 | Production defaults business kosong, fixtures terpisah | Proposal: target, dosis, diskonto, harga dan data contoh bukan nilai produksi |

Izin Git 15 Oktober untuk publikasi yang disebutkan tetap historis berlaku sesuai scope; jangan menganggapnya izin deploy, force push, mengganti remote, atau menggabungkan fitur berbeda. Task ini tidak commit/push karena perubahan context sebelumnya belum dipisahkan menjadi commit dan pengguna meminta paket perencanaan, bukan publikasi.

## Keputusan terbuka dan pekerjaan independen

| ID | Pertanyaan / pemilik | Menahan | Yang tetap boleh dikerjakan |
|---|---|---|---|
| Q01 | Mitra: penjualan selalu lunas? retur/kerusakan/pembatalan bagaimana? | Perilaku retur/kredit dan acceptance penjualan | Model awal paid-only, saldo, test fixture; jangan tambah piutang |
| Q02 | Mitra: dasar alokasi biaya dan penyusutan per waktu/proporsi/musim? | Otomasi alokasi produksi | Alokasi eksplisit + basis, plafon concurrency, penyusutan dasar |
| Q03 | Mitra: PP/DPP interpolasi atau akhir tahun? actual/projection sourcing? | Default laporan investasi resmi | Kedua strategi berlabel/configured; fixture interpolasi proposal |
| Q04 | Mitra: kolom wajib per aktivitas, satuan, target/toleransi, jadwal selesai bertahap? | Formula agronomis/completion produksi | Schema/form dinamis, draft, comparator null/single/range, tidak auto-complete |
| Q05 | Pengelola: petugas berbeda pencatat; sebelum semai; edit/batal panen terjual? | Perilaku kasus belum ditentukan | Creator dari sesi, koreksi sendiri saat open, saldo negatif ditolak |
| Q06 | Pengelola: logout/offline expiry, lama offline, retensi draft/receipt? | Purge atau batas offline resmi | Isolasi akun, hold queue, tidak hapus draft otomatis; retry idempoten |
| Q07 | Pengelola: batas/jenis/jumlah lampiran; kebijakan file wajib? | Konfigurasi produksi upload | Access layer privat + field config + rejection test dengan nilai fixture |
| Q08 | Pengelola: jadwal/retensi backup, RPO/RTO, operator restore? | Operasional produksi | Backup/restore staging dan manifest/checksum; maintenance/epoch design |
| Q09 | Tim: import data IoT/demo, mapping akun/greenhouse, retensi legacy? | Import/drop/cutover data nyata | Schema additive, importer dry-run bila diminta, data legacy tetap |
| Q10 | Tim: kebijakan scale/range/rounding/currency dan solver IRR? | Freeze kontrak numerik | Eksplorasi fixture dan test oracle; tidak silently round |
| Q11 | Mitra: toleransi non-nominal dan UAT90% disahkan? | Pernyataan acceptance resmi | Uji dan lapor hasil terhadap usulan secara eksplisit |
| Q12 | Pembimbing: pengecualian Android-only; CRUD via pembatalan historis? | Klaim SOP/handover | Implementasi Android dan audit trail sesuai scope |
| Q13 | Tim/mitra: tanggal mulai, deadline, PIC, perangkat/peserta UAT, bukti lapangan? | Timeline kalender dan laporan akademik final | Rencana dependensi, environment check, synthetic tests |

## Konflik/editorial proposal

- PDF2 halaman pengesahan masih berjudul IoT lama; PDF18 paragraf sesudah KNF masih menyebut monitoring realtime. §1.3 dan arsitektur baru secara eksplisit tanpa IoT; baseline produk mengikuti bagian itu, tandai sisa teks untuk revisi akademik.
- PDF54 pembuka alat/bahan masih menyebut monitoring/kalibrasi; tabel alat sudah Android/backend. Tidak menjadi perintah membangun sensor.
- PDF31 Gambar2.8 diberi judul ERD Operasional padahal gambar keuangan; rujuk isi/halaman, jangan mengganti konsep menjadi operasional.
- PDF56 U12/U13 merujuk Tabel2.12/2.13, sedangkan fixture aktual Tabel2.8/2.9 PDF44/46–47. Gunakan fixture aktual.
- Penomoran cetak/daftar isi tidak monoton (PDF39 cetak34, PDF41 cetak29). Semua rujukan paket ini memakai urutan PDF.
- Investasi disebut dalam narasi ERD tetapi diagram keuangan yang tersedia belum menggambarkan seluruh investasi/sync/audit. Model teknis harus melengkapinya sebagai derivasi, bukan mengklaim diagram lengkap.

Tidak mengedit PDF sumber pada task ini. Konflik bukan alasan menghentikan pekerjaan yang tidak bergantung pada keputusan tersebut.
