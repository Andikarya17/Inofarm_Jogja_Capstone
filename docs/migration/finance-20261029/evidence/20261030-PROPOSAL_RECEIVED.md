# Verifikasi salinan proposal30 Oktober2026

Pengguna memberikan PDF asli pada chat lanjutan FIN11–FIN17. Pemeriksaan dilakukan pada branch `codex/finance-assets-entry` dengan dependency `d545649`; source aplikasi tidak diubah karena pemeriksaan PDF.

- Sumber lokal: `C:/Users/Pongo/Downloads/Proposal Aplikasi Pencatatan Keuangan .pdf`.
- SHA256: `e4d7389e2fb1385d43ed507f33d211b7e66f92eae1bade4f15c64c6cf3bfc273`.
- Jumlah:60 halaman PDF. Hash sama persis dengan register baseline29 Oktober2026, sehingga tidak ada revisi proposal baru untuk dimigrasikan.
- Ekstraksi ulang halaman PDF37–50 dan55–57 dengan pypdf; render halaman43,44,46,47 dengan pypdfium2 dan pemeriksaan gambar tabel2.7–2.10. Artefak sementara privat `.runtime/proposal-finance-extract.txt` dan `.runtime/proposal-review/` tidak dipublikasikan.
- Hasil PASS: rumus/kelayakan pembagi evaluasi musim; penyusutan kumulatif maksimal A−S, alokasi eksplisit dan larangan membebankan pemakaian yang sama dua kali; investasi terpisah dari musim; status/reasons/sumber/asumsi dan fixture sintetis sesuai register kontrak aktif.
- Contoh musim: FC5000000, VC3000000, TC8000000, Q400, TR12000000, L4000000, R/C1.50, B/C0.50, BEP266.67kg. Contoh investasi I0=100000000, CF1=CF2=60000000, r=10%: NPV4132231.40, IRR sekitar13.0662%, PP1.6667 dan DPP1.9167 dengan interpolasi. Angka ini hanya oracle sintetis, bukan nilai awal produksi.

Ketersediaan PDF kini terverifikasi. Konvensi PP/DPP yang memerlukan keputusan pengelola tetap tidak otomatis disahkan oleh contoh interpolasi. Gate UAT, pentest, perangkat fisik, pembimbing, akses push yang ditunda dan cutover tetap belum terbukti. Catatan historis bahwa PDF belum tersedia tetap menggambarkan waktu pemeriksaan sebelumnya; tidak menghalangi pekerjaan independen sekarang.
