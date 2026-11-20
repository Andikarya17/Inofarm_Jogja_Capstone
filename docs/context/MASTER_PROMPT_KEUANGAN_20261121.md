# Master prompt: Keuangan biaya standar, grafik dan pembeli (21 November 2026)

Salin teks di bawah garis ke sesi baru (Claude Code di repo ini).

---

Kamu mengerjakan fitur Keuangan pengelola Inofarm. Desain dan rencananya sudah disetujui pengguna; jangan membuka ulang keputusan di bawah kecuali pengguna memintanya.

**Baca dulu, berurutan:**
1. `CLAUDE.md`, `AGENTS.md`, `backend/AGENTS.md`, `DESIGN.md`.
2. Desain: `docs/superpowers/specs/2026-11-21-keuangan-biaya-standar-pembeli-design.md`.
3. Rencana: `docs/superpowers/plans/2026-11-21-keuangan-biaya-standar-pembeli.md`. Kerjakan task demi task, sesuai urutan dan checkbox.
4. Mockup arah tampilan (10 layar): kanvas Inofarm Keuangan, https://claude.ai/artifact/PskzgVNvPFBhH7zvRdwsQW. Baca artboard lewat Artifact `read`. Angka penjualan di sana adalah data contoh.

**Keputusan pengguna yang mengikat:**
- FC dan VC adalah **angka standar yang sama untuk setiap GH**, tanpa isian per GH. Disimpan sebagai satu lembar per Musim ke-n untuk seluruh kebun (pendekatan D).
- Baris wajib FC: Biaya penyusutan, Sewa tanah, Tenaga kerja. Baris wajib VC: Cocopeat/rockwool, Nutrisi AB Mix, Nutrisi tambahan dan pestisida, Benih. Nama terkunci; baris tambahan boleh dibuat dan dihapus.
- TC = FC + VC, dijumlah oleh sistem. Laporan musim memakai biaya standar saja; alokasi dan penyusutan tidak dijumlahkan.
- Biaya nyata (Catat pengeluaran, Alokasi, Aset, Penyusutan) bisa disembunyikan atau ditampilkan dengan **ikon mata**; secara bawaan tersembunyi. Datanya tidak dihapus.
- Grafik Keuangan memakai **batang bertumpuk** Mitra dan Perorangan yang bisa diketuk, untuk minggu, bulan dan tahun. **Tidak ada kurva laba kumulatif.**
- Pembeli perorangan: daftar pembeli yang bisa diimpor dari Excel. Di form penjualan, pengelola mencari dan memilih pembeli; bila belum terdaftar, isi pembeli baru.
- Mitra (tengkulak): grade A/B/C dengan **kg dan harga per kg**, tanpa jumlah buah.
- Retail: hanya tampil "Belum tersedia".
- Tenggat kurang dari 2 minggu. Tahap 1 dan 2 wajib; tahap 3 menyusul setelahnya; tahap 4 bila sempat; PDF investor sesudah tenggat.

**Aturan kerja:**
- Buat branch `keuangan-biaya-standar` dari `main` terbaru (`git fetch github`). Commit per task, hanya file task itu. Jangan stage file lain yang sudah kotor di working tree pengguna.
- Tes database hanya di `inofarm_test`. Jalankan `./scripts/test-backend.ps1 -GuardOnly` sebelum tes terfilter. Jangan migrate, reset atau seed `inofarm_finance_local` tanpa izin eksplisit pengguna dan backup.
- Untuk UI: pakai skill `impeccable` dan `antislop`, **jangan** `ponytail`. Tanpa warna baru dan tanpa em dash di teks.
- Emulator hanya `Inofarm_Botanical_API30`, mode terang. Tanyakan dulu sebelum menerima usulan yang mengubah keputusan pengguna.
- Bedakan bukti baru dari bukti lama, dan fixture dari backend nyata. Jangan klaim perangkat fisik.
- Di akhir: dokumen bukti di `docs/context/`, paragraf status di `CLAUDE.md`, push branch ke remote `github`, buka PR. Jangan merge.

**Hal yang masih ditanyakan ke Inofarm** (jangan ditebak; tulis sebagai celah terbuka): kolom Excel pembeli mereka, dan aturan harga retail.
