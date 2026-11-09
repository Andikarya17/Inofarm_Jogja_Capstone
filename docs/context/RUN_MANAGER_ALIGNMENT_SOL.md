# Prompt eksekusi master pengelola untuk 6.1 Sol

Tempel blok berikut pada chat dengan model 6.1 Sol dalam workspace Inofarm. Nama model pada pilihan chat tidak diganti oleh isi prompt ini.

```text
Kerjakan implementasi penyelarasan fitur PENGELOLA Inofarm sampai acceptance di master prompt terpenuhi. Jangan berhenti pada audit atau rencana baru.

Workspace:
D:\Semester 7\Capstone 2\inofarm-jogja---hydroponic-iot-&-nutrition-manager

Baca dan jalankan:
1. AGENTS.md root dan instruksi lokal yang relevan.
2. docs/context/README.md dan IMPLEMENTATION_STATUS.md untuk keadaan terbaru.
3. docs/context/MANAGER_VISION_AUDIT_20261109.md.
4. docs/context/MASTER_PROMPT_MANAGER_ALIGNMENT.md sebagai instruksi utama task ini.

Baca langsung kedua sumber asli:
- D:\Semester 7\Capstone 2\Proposal\Jadwal  MT 3 pemupukan dan stadia tumbuh Melon GH Krebet_093055.xlsx
- D:\Semester 7\Capstone 2\Proposal\Dwito Mufti Agung_574189_Makalah Analisis Usahatani Meloh Hidroponik.pdf

Visi pengelola:
- Beranda memilih satu/semua greenhouse yang sah, ringkasan kegiatan/panen/keuangan, dua kartu EC/PPM dan pesan jelas bila belum ada pencatatan hari ini.
- Antrean adalah verifikasi kegiatan pekerja lintas perangkat, detail aktual dibanding acuan, valid/tidak valid dengan alasan dan histori. Pertahankan akses outbox/sinkronisasi existing secara terpisah.
- Pengelola juga mencatat kegiatan sesuai kedua sheet Excel.
- Panen memilih GH dan varietas dari periode yang benar, jumlah buah/kg, target dibanding hasil layak, dengan panen bertahap dan histori yang aman.
- Keuangan mencakup investasi/aset, penyusutan, FC/VC/TC, penerimaan, laba, R/C, B/C sederhana, BEP, NPV/IRR/PP/DPP dengan sumber dan periode jelas.
- Kelola greenhouse dan Kelola akun mudah ditemukan; reuse CRUD, edit email dan reset password yang sudah ada.

Gunakan antislop DURING (pilihan eksplisit saya), impeccable project-local mode Operate/native Android, PRODUCT.md dan DESIGN.md. Pertahankan forest/sage/krem, komponen native, data/form solid, kaca selektif dan aset yang sudah disetujui. Jangan bertanya ulang mode, stack atau fitur yang sudah diminta. Jangan mengubah jadi web atau mengembalikan IoT.

Mulai dengan status/diff/ancestry dan baseline HEAD. Audit dibuat pada 8d74cba; telusuri perubahan sesudahnya agar tidak mengerjakan ulang fitur yang sudah selesai. Eksekusi fase PG0-PG7 dalam urutan dependency; branch codex/ per fitur, commit hanya file task sesudah checks. Ikuti otorisasi Git terbaru dan fetch/sync github sebelum push; jangan force-push, auto-merge atau deploy. Jangan menyentuh file pengguna yang tidak terkait.

Pisahkan diterima server, pembandingan target dan keputusan verifikasi manusia. Jangan menganggap pengukuran di luar target sebagai data palsu. Jangan menyamakan populasi tanaman dengan target buah, atau panen dengan penjualan. Makalah mempunyai salah label B/C, subtotal NPV dan ketidakjelasan basis periode; gunakan koreksi yang ditelusuri pada audit, jangan menyalin kesalahan ke engine atau menjadikan angka studi default produksi.

Untuk keputusan bisnis yang belum dijawab, tanyakan satu pertanyaan terarah ketika diperlukan dan lanjutkan bagian independen. Jangan menyebut usulan pada master prompt sebagai persetujuan produksi. Jangan menghapus data/histori, reset database pengguna, clear app data, atau menggunakan akun nyata untuk fixture tanpa otorisasi lingkungan. Backend tests wajib effective MySQL inofarm_test dengan guard sebelum SQL.

Buktikan fitur dengan test kontrak/domain, backend MySQL guarded, JVM, build/lint dan instrumentasi yang relevan. Jalankan alur dua akun pada lingkungan uji nyata, offline/restart/retry/conflict, serta review visual sebelum/sesudah. Mock bukan bukti runtime nyata. Update context dan evidence per fase dengan hasil yang benar-benar dijalankan dan seluruh batasnya.

Teruskan sampai seluruh pekerjaan terotorisasi selesai atau ada hambatan konkret yang memerlukan jawaban saya. Bila terhambat, selesaikan bagian independen, simpan checkpoint yang jelas, lalu jelaskan keputusan yang menahan pekerjaan. Jangan mengklaim UAT/pentest/perangkat fisik/produksi lulus tanpa bukti.
```
