# Audit visual form, wizard dan filter pengelola (17 November 2026)

Cakupan: semua form input pengelola yang memakai `FarmFormFrame` (pengeluaran, alokasi, penjualan, aset, penyusutan, kegiatan, pengukuran, panen, admin/master, akun), wizard `Tambah greenhouse`, sheet `Filter riwayat`, serta daftar dan detail di Kelola. Hanya saran; tidak ada kode yang diubah.

Metode: tinjauan desain dari source (`FormCanopy.kt`, `CanopyKit.kt`, `FarmChrome.kt`, `FinanceRoot.kt` bagian `FinanceField`/`Choice`, `FinancialEntryScreen.kt`, `GreenhouseWizard.kt`, `AdminScreen.kt`, `HistoryScreen.kt`) dan screenshot emulator yang sudah ada (`docs/context/evidence/f5-20261115/light-manager-payment.png`, `light-manager-home-selected.png`, `outputs/frontend-secondary-menus/after/frontend-secondary-light-412/secondary-filter-responsive.png`). Screenshot filter berasal dari 3 November, sebelum Canopy; struktur kodenya sekarang masih sama (lima `Choice` bertumpuk), hanya bingkainya berubah. Detektor web impeccable tidak berlaku untuk Compose. Belum ada pemotretan baru di emulator untuk audit ini.

Mockup sebelum/sesudah: artifact `Usulan Form Pengelola` (tautan di percakapan 17 November 2026).

## Pembacaan desain

Android, mode Operate, pengelola greenhouse melon yang mencatat di lapangan atau setelah belanja, sering satu tangan. Bahasa visual Canopy: kanopi forest di atas, bidang krem solid di bawah, Plus Jakarta Sans, angka tabular. Dial dari DESIGN.md untuk form: ENERGY 1 / RHYTHM 2 / MOTION 2.

## Skor heuristik

| # | Heuristik | Skor | Temuan utama |
|---|---|---|---|
| 1 | Status sistem | 3 | Status draf, `Menyimpan…` dan live region sudah baik. Filter aktif hanya terbaca sebagai kalimat abu kecil. |
| 2 | Bahasa pengguna | 2 | `(YYYY-MM-DD)`, `(HH:mm)`, `Nominal IDR (Rp)` + sufiks `IDR`, `Kosong bukan nol` adalah bahasa sistem. |
| 3 | Kendali pengguna | 3 | Draf, Batal, Back bekerja. Wizard mengunci isian tersimpan tanpa jalan ubah. |
| 4 | Konsistensi | 2 | Tiga anatomi isian dalam satu kartu; lima radius berbeda dalam satu form; header wizard berbeda dari header form. |
| 5 | Pencegahan salah | 3 | Picker, konfirmasi nonaktif, validasi lokal sudah ada. |
| 6 | Kenali, bukan ingat | 2 | Dropdown `Belum dipilih`/`Semua` menyembunyikan pilihan; tiap filter butuh dua sentuhan. |
| 7 | Efisiensi | 2 | Tidak ada preset periode, tidak ada kategori terakhir dipakai. |
| 8 | Estetika minimal | 2 | Kotak dalam kotak dan judul berlapis membuat form terasa padat sekaligus polos. |
| 9 | Pemulihan salah | 2 | Ringkasan kesalahan diulang di bawah form, jauh dari isian. |
| 10 | Bantuan | 3 | Bantuan ada di dekat isian, tetapi terlalu panjang. |
| **Total** | | **24/40** | **Acceptable** |

Beban kognitif: 3 dari 8 butir gagal (hierarki visual, maksimal 4 pilihan per keputusan pada detail greenhouse, pengungkapan bertahap pada filter). Sedang.

## Vonis kekhasan

Hero forest, dock dan kartu pengukuran sudah milik Inofarm. Form belum: kalau logo diganti, form pengeluaran bisa jadi form aplikasi mana pun yang memakai Material outlined field. Penyebabnya bukan warna; tokennya sudah tepat. Penyebabnya komposisi: semua bagian diberi bobot yang sama (kartu bergaris yang sama, judul yang sama, isian yang sama), sehingga tidak ada satu titik fokus dan tidak ada detail yang hanya dimiliki pencatatan greenhouse.

## Yang sudah bekerja

- Token Canopy (forest, sage, krem, amber) dan Plus Jakarta Sans dengan `tnum` memberi dasar yang kuat; tidak perlu warna baru.
- Perilaku form matang: `FarmFormBar` tetap di atas keyboard, status `Menyimpan…` hanya muncul setelah 300 ms, `FarmFieldPair` menumpuk pada font besar, target sentuh 48 dp, semantik radio pada chip.
- `FarmWhenCard` (waktu sebagai satu kalimat terbaca dengan `Ubah`) adalah pola terbaik di form saat ini, tetapi baru dipakai pengukuran dan panen.

## Temuan prioritas

Nomor ini dipakai juga di mockup. Kode aturan antislop di akhir tiap temuan.

### 1. [P1] Kotak dalam kotak, tiga garis bertumpuk

Setiap bagian adalah `Surface` bergaris 1 dp radius 20 (`FormCanopy.kt:105`). Di dalamnya isian bergaris radius 14 (`FinanceRoot.kt:1594`), pemilih bergaris radius 12 dengan warna `outline` yang lebih gelap (`FinanceRoot.kt:1635`), dan chip bergaris radius 12 (`CanopyKit.kt:300`). Rincian berulang menambah lapisan keempat (`FarmDetailCard`, `FormCanopy.kt:240`). Mata membaca tiga tepi melengkung dengan jarak dan ketebalan berbeda di satu area, inilah yang terasa "kurang simetris".

Saran: bagian form tidak bergaris. Pakai bidang tonal `surface` di atas `background` (radius 20, tanpa stroke) atau cukup judul bagian dan jarak 24 dp. Garis hanya milik kontrol yang bisa diisi. Satu warna garis istirahat (`outlineVariant`) dan 2 dp `primary` saat fokus. *R-12, R-14, R-11.*

### 2. [P1] Tiga anatomi isian dalam satu kartu

Di kartu `Pembayaran`: `Kategori pengeluaran` labelnya di dalam kotak (`Choice`, `FinanceRoot.kt:1639`), `Jenis pembayaran` labelnya di atas chip, `Tanggal pembayaran` labelnya di atas kotak teks. Tinggi, padding dalam, radius dan warna garisnya juga berbeda.

Saran: satu anatomi `FarmField` untuk semua: label di atas (labelLarge semibold), kontrol 56 dp radius 12, teks bantu di bawah. `Choice` di dalam `LocalCanopyForm` digambar sebagai isian pilih dengan anatomi yang sama (nilai di dalam, chevron di kanan), bukan tombol outlined dengan dua baris teks. *R-31; heuristik 4.*

### 3. [P1] Tidak ada titik fokus; nominal sama beratnya dengan keterangan

Form pengeluaran membuka dengan strip forest 72 dp yang berisi satu kata (`FarmContextStrip`, planting `null`, `FinancialEntryScreen.kt:205`), lalu nominal di kartu yang sama seperti kartu keterangan. Bidang paling gelap di layar justru konteks, bukan angka yang sedang diisi. DESIGN.md sendiri mencatat celah "Kartu forest 34 sp untuk nominal pengeluaran belum ada".

Saran: nominal menjadi hero form. Bidang forest radius 24, label `Nominal pembayaran`, angka 34 sp tabular sage dengan `Rp` kecil di depan, bantuan satu baris. Konteks greenhouse turun menjadi subjudul header (`GH Melon Timur · Musim 3`). Pola yang sama untuk penjualan (total), aset (nilai perolehan), panen (buah dan kg). Ini memberi form identitas yang hanya dimiliki Inofarm: angka uang dan hasil panen di bawah kanopi. *Liveliness: satu titik fokus, motif identitas.*

### 4. [P1] Filter adalah tumpukan lima dropdown, dan hasilnya tidak terlihat

`HistoryFilters` (`HistoryScreen.kt:290-353`) menyusun Greenhouse, Musim, Kelompok, Jenis, Status sebagai `Choice` bertumpuk yang semuanya bernilai `Semua`, masing-masing membuka dialog (dua sentuhan per filter). Pengelola mengetik tanggal `YYYY-MM-DD`. Setelah diterapkan, filter hanya tampil sebagai kalimat `bodySmall` abu di samping tombol `Filter · 2`.

Saran:
- Di daftar: baris chip filter aktif yang bisa dihapus satu per satu (`GH Melon Timur ×`, `November ×`) plus `Hapus semua`, di bawah chip jenis. Tombol `Filter` menjadi chip berikon dengan angka.
- Di sheet: pilihan pendek tampil langsung sebagai chip (Greenhouse bila ≤ 6, Status sebagai segmen `Semua | Aktif | Dibatalkan`). Musim dan Kelompok tetap pemilih karena panjang dan bergantung pada greenhouse.
- Periode memakai preset (`Hari ini`, `7 hari`, `Bulan ini`, `Musim ini`, `Atur tanggal`); `Atur tanggal` membuka pasangan `Dari | Sampai` dengan picker Android, sama untuk pekerja dan pengelola.
- Bar bawah tetap: `Atur ulang` dan `Terapkan`. Bila jumlah hasil bisa dihitung dari cache lokal, labelnya `Tampilkan 24 catatan`.

*R-27 bagian kosong sudah ada; heuristik 6 dan 7.*

### 5. [P2] Chip pilihan tidak simetris

`FarmChoiceChips` memakai `FlowRow` dengan lebar intrinsik (`CanopyKit.kt:309`). `Pembayaran operasi` lebih lebar dari `Pembelian aset`, tepi kanannya bergerigi dan tidak sejajar dengan isian di atas dan bawahnya. Sama pada `Biaya tetap / FC | Biaya variabel / VC` dan `Layak | Cacat`.

Saran: untuk 2 sampai 3 pilihan eksklusif, pakai segmen penuh lebar dengan bobot sama (pola Material segmented button, tanda centang pada yang terpilih). `FlowRow` tetap untuk 4 pilihan atau lebih. Tag wadah dan `$tag-option-$id` dipertahankan. *R-11; heuristik 4.*

### 6. [P2] Wizard: judul berlapis, Lewati di badan, layar selesai polos

Langkah 1 berisi satu isian di bawah empat lapis judul: batang langkah, `Langkah 1 dari 3`, judul `Greenhouse`, judul kartu `Data greenhouse`, label `Nama greenhouse` (`GreenhouseWizard.kt:263-286`). Langkah 2 menaruh lima isian dalam satu kolom panjang tanpa pasangan. `Lewati langkah ini` ada di badan, jauh dari ibu jari. Layar selesai hanya tiga baris teks (`GreenhouseWizard.kt:251-261`). Headernya `FarmSheetHeading` + divider, berbeda dari header form (`FarmFormHeader` dengan X).

Saran:
- Header sama dengan form: X, `Tambah greenhouse`, dan `2/3` di kanan.
- Stepper bernomor dengan label di bawah (selesai: lingkaran brand bercentang; aktif: cincin brand; belum: garis). Batang dan teks `Langkah N dari 3` digabung, teks tetap untuk TalkBack.
- Judul langkah satu baris plus satu kalimat mengapa langkah ini ada. Langkah satu isian tidak memakai kartu.
- Langkah 2: `Jumlah tanaman | Tanggal semai` sebagai pasangan; `Tanggal pindah tanam` di balik `+ Tambah tanggal pindah tanam`.
- Bar: `Lewati` (tombol teks) di kiri, aksi utama di kanan. Kembali lewat ikon panah di header dan Back sistem.
- Langkah 3: pekerja yang ada sebagai baris pilihan dengan avatar inisial; `Buat akun pekerja baru` tertutup di balik baris `+`.
- Layar selesai: hero forest dengan centang, nama greenhouse, daftar tiga baris status (tersimpan / ditetapkan / pekerja ditambahkan atau `Atur nanti` amber), lalu `Catat kegiatan` dan `Ke greenhouse`.

*Liveliness: momen akhir; R-05 bukan tiga langkah templat karena langkahnya nyata.*

### 7. [P2] Label teknis dan bantuan terlalu panjang

`Nominal IDR (Rp)` + sufiks `IDR` + judul kartu `Nominal pembayaran` menyebut hal yang sama tiga kali. Bantuan nominal tiga baris (`FinancialEntryScreen.kt:209`). Format mesin ada di label: `Tanggal pembayaran (YYYY-MM-DD)`, `Jam pembayaran WIB (HH:mm)`, `contoh 2026-11-10`.

Saran: label manusiawi (`Tanggal bayar`, `Jam`), isian tanggal menampilkan `Sab, 15 Nov 2026` dan jam `09.30 WIB`, bantuan satu baris (`Titik ribuan muncul sendiri.`). Format ketik tetap diterima di belakang layar. Waktu pembayaran memakai `FarmWhenCard` seperti pengukuran dan panen. *Heuristik 2; R-16 tidak berlaku, ini kejelasan bukan buzzword.*

### 8. [P2] Kelola: kartu per baris dengan tombol teks bergerigi; detail dengan tujuh tombol

Setiap baris daftar adalah `FarmPanel` berisi 2 sampai 3 `TextButton` dalam `FlowRow` (`AdminScreen.kt:198-222`), sehingga panjang baris berbeda-beda. Detail greenhouse memuat `Ubah`, `Nonaktifkan`, `Tambah musim`, `Tambah varietas`, `Tambah kelompok tanam`, `Ganti tanam saat ini`, `Atur pekerja` sebagai tombol teks yang membungkus (`AdminScreen.kt:143-162`): tujuh pilihan setara, termasuk tindakan merusak.

Saran: daftar sebagai baris (nama, satu baris keterangan, chip status di kanan, chevron), ketuk membuka detail. Detail: kartu ringkas di atas, lalu satu kelompok baris aksi berikon (`Tanam saat ini`, `Musim`, `Varietas`, `Kelompok tanam`, `Pekerja`, `Ubah nama`) memakai `FarmActionRow`, dan `Nonaktifkan greenhouse` terpisah di bawah dengan warna error. *Beban kognitif: lebih dari 4 pilihan setara.*

### 9. [P2] Kesalahan diulang di bawah form

`state.errors.values.distinct().forEach { Notice(it, true) }` (`FinancialEntryScreen.kt:237`) mengulang pesan yang sudah ada di bawah isian, di posisi yang biasanya tertutup bar. Pengguna menekan `Periksa`, tidak melihat apa pun berubah di layar.

Saran: satu ringkasan di atas form (`2 isian perlu diperbaiki`) yang menggulir ke isian pertama yang salah dan memberi fokus. Pesan per isian tetap di tempatnya. *Heuristik 9.*

### 10. [P3] Dua judul di layar form

Screenshot pengeluaran menampilkan app bar shell `Keuangan` + avatar, lalu header form `X Catat pengeluaran`. Pada kedalaman form, shell bisa menyembunyikan app bar sehingga header form menjadi bar atas satu-satunya.

### 11. [P3] Radius bergeser

Form saja memakai 12 (kontrol, chip), 14 (isian, rincian), 16 (strip konteks, tombol bar), 20 (kartu bagian), 24 (hero), ditambah 18 dan 14 di tab segmen (`CanopyKit.kt:243`, `:253`). Saran: tiga nilai untuk form: 24 hero, 20 bidang bagian, 12 semua kontrol (isian, pemilih, chip, tombol bar). Rumus konsentris (radius luar = radius dalam + padding) hanya berlaku ketika kedua tepi terlihat; dengan temuan 1, tepi bagian tidak lagi bergaris, jadi selisih radius tidak terbaca.

### 12. [P3] Strip konteks berat untuk informasi kecil

`FarmContextStrip` setinggi 72 dp dengan ubin ikon 40 dp walau hanya ada nama greenhouse. Lihat temuan 3: pindahkan ke subjudul header, atau buat menjadi chip forest 40 dp yang bisa diketuk untuk mengganti greenhouse bila form mengizinkan.

## Persona

- **Pengguna baru (Jordan):** membaca `Nominal IDR (Rp)` lalu `IDR`, ragu harus mengetik titik atau tidak; membaca `(YYYY-MM-DD)` dan mengetik `15/11/2026`. Di filter, lima kotak `Semua` tidak memberi petunjuk mana yang penting.
- **Pembaca layar (Sam):** label di atas isian dibersihkan dari semantik dan dipindah ke `contentDescription` (`FinanceRoot.kt:1591`, `:1606`), ini benar. Ringkasan kesalahan di bawah form diumumkan sebagai live region, tetapi fokus tidak berpindah ke isian yang salah. Stepper wizard tersembunyi dari semantik dan bergantung pada teks `Langkah N dari 3`, ini benar dan harus dipertahankan saat stepper diganti.
- **Satu tangan di greenhouse (Casey):** `Lewati langkah ini` dan `Hapus` tanggal filter ada di tengah layar; pilihan dropdown membuka dialog di tengah. Aksi yang sering harus ada di bar bawah atau dalam jangkauan chip.

## Rencana tindakan yang disarankan

1. `$impeccable layout`: temuan 1, 2, 5, 11, 12 di `FormCanopy.kt`, `CanopyKit.kt`, `FinanceField`/`Choice`. Satu perubahan komponen bersama memperbaiki semua form sekaligus.
2. `$impeccable bolder` (dengan dial ENERGY tetap 1 di badan form): temuan 3, hero nominal untuk pengeluaran, penjualan, aset, panen.
3. `$impeccable layout` + `$impeccable clarify`: temuan 4 dan 7, filter dengan chip aktif dan preset periode, label manusiawi.
4. `$impeccable onboard`/`delight`: temuan 6, wizard dan layar selesai.
5. `$impeccable distill`: temuan 8, daftar dan detail Kelola.
6. `$impeccable harden`: temuan 9, ringkasan kesalahan dan fokus.
7. `$impeccable polish`: pemotretan terang, gelap, 200% dan 360 dp di emulator untuk semua form.

Risiko tes: banyak tes UI menyentuh tag `*-option-$id`, `history-*`, `wizard-*`, `admin-*`. Semua tag harus dipertahankan; `FinanceEntryUiTest` masih punya kegagalan baseline, jadi bandingkan per nama tes sebelum dan sesudah.

## Sumber referensi

- Compose `TextFieldLabelPosition` (label di atas / di dalam): https://developer.android.com/reference/kotlin/androidx/compose/material3/TextFieldLabelPosition.Attached
- Segmented button untuk pilihan eksklusif: https://kotlinlang.org/api/compose-multiplatform/material3/androidx.compose.material3/-segmented-button.html
- Filter chip vs segmen: https://www.composables.com/material3/filterchip
- Pola filter, chip aktif yang bisa dihapus, terapkan dari sheet: https://uiux-atlas.pages.dev/lessons/ui-patterns/filtering-and-sorting · https://baymard.com/learn/ecommerce-filter-ui · https://design.visa.com/patterns/filters · https://www.sap.com/design-system/fiori-design-android/v26-4/components/filter-feedback-bar/usage
- Wizard di ponsel: indikator ringkas, kembali tanpa kehilangan data, langkah periksa: https://www.reform.app/blog/mobile-multi-step-forms-responsive-ux-guide · https://ezud.com/accessible-multi-step-wizard-forms/ · https://www.uxpin.com/studio/blog/design-progress-trackers/
