# Sistem visual Android Inofarm

Aplikasi kerja greenhouse untuk pekerja dan pengelola. Hierarki bergerak dari konteks budidaya ke hasil ukur dan tindakan. ENERGY 2 / RHYTHM 2 / MOTION 2. Material 3 memberi peran aksesibilitas, interaksi sentuh, Back, keyboard, dialog dan sheet.

## Komposisi

- Masuk memakai panel solid terpusat dengan batas 420 dp. Email, Password dan tombol Masuk mengikuti urutan fokus yang sama dengan urutan baca.
- Beranda menempatkan kartu konteks di atas dua kartu pengukuran. Kartu konteks menonjolkan nama greenhouse, lalu musim, kelompok dan umur. EC dan PPM berbagi anatomi label, angka, satuan dan sumber. Susun vertikal saat lebar konten di bawah 268 dp atau font scale di atas 1,3.
- Kartu riwayat mendahulukan judul dan nominal yang tersedia, lalu konteks, waktu dan status sinkronisasi. Perbandingan konflik memisahkan versi lokal dan server menjadi panel tersendiri. Antrean menampilkan jumlah pengajuan yang menunggu dan perlu ditinjau.
- Form memisahkan waktu, sumber atau konteks, hasil aktual dan catatan menurut kebutuhan. Review memakai panel ringkasan dan aksi antrekan yang terpisah dari kembali ke isian. Keuangan memakai satu daftar gulir dan panel alat terpisah.

## Token dan material

`InofarmTheme.kt` mendefinisikan skema terang dan gelap. Hijau kebun menandai aksi dan navigasi aktif. Amber menandai pengajuan yang perlu ditinjau dan musim tertutup. Teks menyatakan status secara eksplisit. Warna EC dan PPM membedakan dua pengukuran yang setara. Sans sistem mempertahankan reflow dan skala pengguna serta mendukung teks dan angka tanpa aset font tambahan.

Halaman dan panel memakai padding 16 dp. Radius 12 dp membedakan kontrol dari kartu 20 dp dan navigasi 26 dp. Komponen aksi utama memakai target 48 dp. Pada lebar sempit dan skala teks tinggi, navigasi memakai dua baris dan label field berpindah ke atas isian agar kata dan label dapat tumbuh. Field, angka, panel review dan pesan kesalahan memakai latar solid untuk menjaga kontras.

Navigasi bawah adalah satu-satunya permukaan kaca. Di beranda pada API 31+ dengan akselerasi hardware dan skala teks sampai 1,4, konten dapat bergulir di bawah lapisan tint dengan opasitas 94%. Tepi terang dan elevasi tipis memisahkan kontrol dari konten. Bentuk dan warna dasar yang sama dipakai pada fallback solid untuk API 29/30, layar berskala teks lebih besar dan tab lain. Fallback memberi ruang terpisah untuk daftar sehingga dua baris navigasi tidak menutupi konten. Implementasi memakai komposit alpha dalam window yang sama, dengan ikon dan label opak.

## Motion dan status

Indikator tab dan warna status draf berubah dalam 200 ms. Review masuk dengan fade dan pergeseran 12 dp selama 220 ms, melalui layer visual di atas satu konten review. Sheet memakai transisi Material. Panel audit dan rumus menggunakan perubahan tinggi untuk menunjukkan hubungan dengan detail yang dibuka. Nilai EC, PPM dan uang langsung menampilkan nilai aktual. Compose mengikuti skala animasi sistem.

Sumber data, makna status, alasan penolakan dan ownership ViewModel mengikuti kontrak operasional. Bukti render dan verifikasi dicatat dalam context frontend.
