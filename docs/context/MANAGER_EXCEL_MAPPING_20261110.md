# Pemetaan dua sheet jadwal MT 3 GH Krebet

Preview baca saja dari workbook asli dengan SHA-256 `39091a131b63439f7223615e8872b34449c4c4f6ab8dfe411f78c8d7baf65aa9`. Rentang berikut mencakup semua sel kegiatan yang terisi. Baris nomor tanpa nama kegiatan dan judul bagian dikecualikan. Tidak ada angka, bahan, atau tanggal studi yang dimasukkan ke data produksi.

| Sel sumber | Basis jadwal | Representasi aplikasi |
| --- | --- | --- |
| Sheet1 B6:E15, B16:E90 | HSS 1–10, lalu HST 1–75 menurut reset C16 | Target PPM dan pH yang ditinjau per kelompok/waktu. Nilai aktual di Pengukuran atau Nutrisi, waktu WIB. EC target memerlukan nilai independen. |
| Sheet1 F6, F16, F23, F31, F44, F71 | Umur pada kolom C | Rencana stadia sebagai acuan. Stadia aktual bertanggal pada jenis Stadia. |
| Sheet1 G6:G90 | Kolom rencana penambahan L, seluruh sel data kosong | Nutrisi menyimpan bahan, jumlah aktual dan satuan. Tidak ada dosis otomatis. |
| Sheet1 H80, H90 | HST 65 dan 75 | Rencana panen Dalmatian dan Yellow Hammi. Panen aktual berasal dari varietas kelompok dan rincian buah/kg. |
| Sheet1 J6:M9, J11:M12, J14:M15, J17:M21, J23:M24, J26:M27, J29:M30, J32:M32, J34:M36, J38:M38, J40:M40, J42:M42, J44:M48 | HST pada J, waktu rencana pada K/M | Penyemprotan. Bahan aktual dapat lebih dari satu, satuan dan jumlah diperlukan untuk pekerjaan selesai. L9 menamai dua bahan tanpa kuantitas. Kegiatan dilewati menyimpan alasan. |
| Jadwal general C4:C9, C11, C15:C22 | HSS 1–10 | Pekerjaan umum untuk benih, bibit, penanganan HPT, kebersihan dan persiapan. Nama pekerjaan dan detail di keterangan. |
| Jadwal general C25 | HST 1 | Pengukuran EC mS/cm, PPM ppm dan pH opsional. DO mg/L dan suhu air °C opsional. Pengukuran tersimpan tanpa bahan. |
| Jadwal general C31, C42, C48, C59 | HST 3, 8, 11, 24 | Pekerjaan umum dengan tinggi cm, jumlah daun helai dan lebar cm opsional, serta basis sampel. |
| Jadwal general C26, C29, C35, C38, C41, C49, C65, F4, F13, F19, F23, F27, F31 | HST pada B/E | Penyemprotan tanaman/lantai dan pengendalian HPT. Simpan bahan dan satuan aktual setelah selesai. |
| Jadwal general C28, C30, C32, C34, C36:C37, C40, C44:C47, C50:C54, C56:C57, C61:C63, C66:C67 | HST pada B | Pekerjaan umum untuk deteksi HPT, peroksida, screen, klip, pinching, polinasi dan fungisida. Detail pekerjaan pada jadwal/catatan aktual. |
| Jadwal general F3, F5:F7, F9:F12, F14, F16:F18, F20:F21, F25:F26, F28:F30, F32 | HST pada E | Pekerjaan umum untuk fertigasi, seleksi, perawatan, penomoran buah, pemeriksaan drip, kebersihan dan tanaman sakit/tidak berbuah. Taksiran tanaman memakai satuan tanaman di catatan, terpisah dari grade buah. |
| Jadwal general F22, F33, F36 | HST 42, 49 dan 60 | Taksasi, estimasi dan pencatatan hasil pemeriksaan. Keterangan mencatat metode dan basis. Target panen eksplisit dan hasil layak/cacat mengikuti alur Panen. |
| Jadwal general F35, F37, F39:F42, F44:F50 | HST pada E | Persemaian lanjutan, panen, penimbangan dan pascapanen. Panen/berat aktual dicatat sekali pada Panen. Angkut, kemas, sterilisasi dan sanitasi dapat dicatat sebagai pekerjaan umum. F43 adalah judul bagian. |

## Tinjauan sebelum menerbitkan acuan

- K21 dan K20 punya tanggal berbeda untuk HST 25. K34:K36 dan tanggal terisi K38:K48 merujuk April 2024/2025 sementara baris sekitarnya berada pada musim Oktober–Desember 2025.
- E88:E90 berisi `5.8-6.10`, `5.8-6.11`, dan `5.8-6.12`. Sumber tidak memberikan keputusan cara membacanya sebagai rentang desimal.
- Jadwal general berpindah HSS ke HST setelah C23. Entri semai F35 berada dalam urutan HST 60, sehingga perlu identifikasi kelompok semai baru sebelum penjadwalan.
- Jadwal adalah rencana. Realisasi mengacu pada ID jadwal dan menyimpan versi saat dicatat. Lewatnya waktu rencana tidak membuat realisasi atau membuktikan pekerjaan dilewati.
