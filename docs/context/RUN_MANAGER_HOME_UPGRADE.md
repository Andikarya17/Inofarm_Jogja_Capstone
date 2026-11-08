# Prompt menjalankan upgrade Beranda pengelola

Pilih GPT 6.1 Sol pada aplikasi, buka repository Inofarm, lalu kirim teks berikut. Prompt ini memulai implementasi; pembuatan dokumen master prompt sendiri belum mengubah runtime.

```text
Implementasikan seluruh instruksi dalam docs/context/MASTER_PROMPT_MANAGER_HOME.md pada repository ini sampai kelima upgrade Beranda pengelola terintegrasi dan diuji.

Gunakan GPT 6.1 Sol yang telah saya pilih. Baca AGENTS.md root/lokal, konteks aktif dan kontrak v2 yang dirujuk sebelum mengubah source. Source terbaru menentukan baseline; master prompt adalah target, bukan bukti fitur telah tersedia.

Wajib gunakan skill Impeccable untuk desain Android native dan antislop mode DURING. Mode DURING sudah menjadi pilihan saya; jangan menanyakan mode lagi. Pertahankan identitas botani, hijau forest, sage, krem, komponen dan navigasi aplikasi saat ini. Buat hierarki dan komposisinya menarik serta mudah dibaca, lengkap dengan animasi singkat yang membantu perubahan cakupan, membuka ringkasan dan navigasi. Hormati pengaturan animasi Android dan jangan menganimasikan angka pengukuran/keuangan secara menyesatkan.

Selesaikan lima area: pilihan cukup greenhouse; ringkasan semua greenhouse setelah login; perhatian berbasis bukti; ringkasan satu greenhouse dengan tautan detail; keuangan sederhana dengan basis yang tepat. Implementasikan dukungan backend/cache/kontrak yang diperlukan, bukan hanya tampilan dengan fixture. Pertahankan histori, draf/outbox, otorisasi dan ketepatan uang; bedakan periode budidaya berjalan dari musim yang dibuka kembali untuk koreksi.

Mulai dengan inspeksi baseline dan rencana singkat, lalu langsung kerjakan bertahap. Jangan berhenti setelah membuat rencana atau meminta persetujuan ulang untuk pekerjaan reversible yang sudah tercakup. Jangan melakukan deploy, merge main otomatis, penghapusan data, atau reset perangkat/database pengguna. Test backend hanya pada effective MySQL inofarm_test dengan guard aktif.

Ikuti acceptance MH-01 sampai MH-14, lakukan build/test/render yang relevan, periksa desain dan animasi pada aplikasi nyata, dan simpan evidence serta update context. Ikuti workflow branch/commit/push GitHub dalam AGENTS.md dan pertahankan perubahan milik pengguna. Laporkan hasil dan batas pengujian secara jujur; mock bukan bukti runtime database atau UAT.
```
