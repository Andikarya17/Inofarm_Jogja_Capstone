# Audit frontend menu lanjutan

Baseline source `dc97705`, branch asal `codex/android-botanical-frontend`. Metode: review native Compose dan screenshot existing, Anti Slop DURING, Impeccable Operate. Dokumen ini bukan hasil implementasi atau pengujian runtime baru.

## Scope dan keputusan pengguna

Antrean, Kegiatan dan Panen untuk kedua peran; Keuangan untuk pengelola. Pengguna memilih seluruh alur turunan, termasuk form, detail, filter, review, konflik serta alat/laporan keuangan. Background botani mengikuti dashboard pada halaman utama empat menu; form/detail tetap solid. Login dan dashboard menjadi referensi yang dipertahankan.

## Bukti

Source: `android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt`, `history/HistoryScreen.kt`, `harvest/HarvestScreen.kt`, `expenses/FinancialEntryScreen.kt`, serta `DESIGN.md` terbaru. Screenshot yang dibuka berada di `outputs/frontend-botanical/final-light-412/`: `worker-measurement-pair-accepted.png`, `worker-history.png`, `manager-finance.png`, `manager-finance-tools.png`, `manager-payment-review.png`.

Capture tersebut berasal dari pekerjaan sebelumnya, dengan data sintetis, bukan capture atau data usaha baru. Sampel visual konsisten dengan struktur source yang diperiksa. Nama file `manager-finance-tools.png` ternyata menampilkan hub, bukan sheet terbuka; tidak dipakai untuk mengklaim sheet sudah diinspeksi visual. Antrean dan Panen dinilai dari source, bukan observasi render baru. Tidak memberi skor performa/aksesibilitas menyeluruh dari gambar.

Panduan native Impeccable dipakai; detector HTML/overlay browser tidak berlaku. Context launcher sudah pernah gagal selesai pada sesi ini, sehingga context proyek dibaca langsung. Tidak ada subagent, build, emulator atau click-through baru dalam audit ini.

## Temuan

| ID | Prioritas | Temuan dan bukti | Arahan |
|---|---|---|---|
| M01 | P2 | `FinanceRoot.kt:213` membatasi `BotanicalBackdrop` pada `showNavigation && tab == 0`. Screenshot Kegiatan/Keuangan menunjukkan latar polos, berbeda dari beranda | Perluas ke halaman utama empat menu secara eksplisit, bukan menghapus guard hingga semua detail/form mendapat wallpaper |
| M02 | P2 | `HistoryScreen.kt` memakai judul besar, tombol filter tersendiri dan pesan sync sebelum baris catatan. Sampel Kegiatan terasa seperti daftar terpisah dari kartu dashboard | Header forest ringkas, filter sebagai toolbar, status dalam bidang stabil, daftar solid dengan radius/border keluarga dashboard |
| M03 | P2 | `FinancialHub` dalam `FinancialEntryScreen.kt` menumpuk konteks, aksi, alat, pemilih riwayat, judul riwayat dan filter. Screenshot menunjukkan beberapa heading/kontrol besar sebelum isi | Susun satu header konteks, aksi utama, akses alat yang jelas, toolbar jenis/filter dan daftar. Jangan menambah KPI fiktif untuk mengisi ruang |
| M04 | P2 | Antrean dalam `FinanceRoot.kt:311` memakai hitungan sebagai teks lepas dan tiap operasi sebagai `FarmSection`, dengan ID/percobaan serta pesan teknis berulang | Ringkasan status yang bersumber dari antrean nyata, item solid berhierarki jenis/status/alasan/aksi. ID dan percobaan sebagai metadata sekunder, semua alasan tetap tersedia |
| M05 | P2 | Panen memakai shared history dan form `FarmSection` per grade. Tidak ada bukti render baru untuk menilai keseluruhan hasil | Terapkan shell riwayat konsisten; susun grade dengan nama, buah, kg dan saldo/version existing yang terbaca. Capture multi-grade/detail wajib sebelum acceptance |
| M06 | P3 | Review pengeluaran sudah memiliki panel rounded, nominal menonjol dan status lokal yang jujur. Form Panen sudah memakai section. Klaim bahwa seluruh turunan belum diubah akan keliru | Reuse yang sudah baik. Audit turunan satu per satu, bukan rewrite massal atau menambah panel dekoratif di seluruh field |

P2 di sini adalah perbaikan konsistensi/hierarki, bukan bug yang terbukti menghalangi transaksi. Kriteria Anti Slop: R-03 reflow, R-14 hierarki panel, R-20 identitas, R-27 state, R-31 alasan desain, R-38 kejujuran data. Aturan ini digunakan untuk penilaian manual, bukan hasil detector.

## Bahasa visual yang perlu diteruskan

Dashboard terbaru memakai bingkai daun, konteks forest, bidang angka krem solid, border lembut, tombol hijau dan navigasi sage. Kesamaan antarmenu harus berasal dari token, header, card/list, toolbar dan state yang sama, bukan wallpaper saja. Jangan menyalin dua kartu EC/PPM menjadi dua kartu statistik tanpa sumber pada setiap menu.

ENERGY 2 / RHYTHM 2 / MOTION 1 untuk menu dan daftar. Form/detail ENERGY 1 / RHYTHM 2 / MOTION 1. Focal point: tindakan yang diperlukan pada Antrean, catatan pada Kegiatan, hasil per grade pada Panen, transaksi pada Keuangan. Background mengikat identitas; surface solid melindungi keterbacaan.

## Keterbatasan dan gate

Turunan penjualan, aset, alokasi, penyusutan, laporan, investasi, lampiran dan recovery harus diinventarisasi serta dicapture saat implementasi. Belum ada temuan visual terkonfirmasi untuk setiap state layar tersebut. Uji kontras, TalkBack, font besar, keyboard, Back, performa dan lifecycle belum dijalankan ulang. Audit tidak mengubah status UAT/pentest atau backend.

Delivery Gate dokumen: arah dan scope dikonfirmasi; bukti existing/source dibedakan; tidak ada aset/data baru; alasan tiap arahan tertulis. Gate implementasi tetap terbuka, bukan PASS UI baru. Tindak lanjut ada di [master prompt menu lanjutan](MASTER_PROMPT_SECONDARY_MENUS.md).
