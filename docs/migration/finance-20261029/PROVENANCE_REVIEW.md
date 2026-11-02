# Review provenance sebelum publikasi context pack

29 Oktober 2026, branch `codex/finance-context-baseline`, base runtime FIN06 `ff5869f`. Pengguna meminta pelaksanaan master prompt dan mengoreksi tujuan publikasi menjadi GitHub. Branch ini hanya dokumentasi/provenance/instruksi; tidak menambah source aplikasi, menjalankan SQL atau mengimpor data legacy.

Paket FIN00 sudah modified/untracked saat implementasi dimulai. Root README/AGENTS/backend AGENTS dan lima context aktif berisi baseline proposal baru; versi terdahulu disimpan pada archive. Inventory SOURCE_INVENTORY/DOCUMENT_CHECKS dan audit tetap bukti statis sebelum implementasi, bukan bukti runtime baru. Kontrak/evidence implementasi berada di contracts/v2 dan evidence/FIN01–06.

## Arsip

Delapan entri manifest diverifikasi dengan SHA256 bytes. Tujuh cocok pada pemeriksaan awal; README arsip tidak cocok karena teks indeks menempati path file README historis. Teks tersebut dipertahankan tanpa perubahan sebagai `INDEX.md`; README historis dipulihkan dari `9da5b30:README.md` dengan CRLF, menghasilkan hash tepat `80f014dc94cf8940163007098a7514ad12f19fabf166231c8ca2f93b29e8b944` yang sudah dicatat manifest. Tidak mengarang hash baru agar cocok. Setelah repair8/8 cocok.

AGENTS root dan README historis cocok HEAD9da5b30 setelah EOL CRLF; backend AGENTS cocok bytes HEAD. Lima context historis **berbeda dari HEAD**, sesuai keterangan bahwa ada perubahan lokal sebelum FIN00. Kelimanya tidak diubah; hash tetap cocok manifest. Isinya dipublikasikan sebagai histori dengan larangan memakai scope lama untuk mengatasi proposal terbaru, bukan dianggap requirement baru atau karya implementasi turn ini.

Archive `.gitattributes` menetapkan -text supaya Git tidak menormalisasi bytes CRLF dan merusak hash pada clone Linux/Windows; JSON metadata mengenali CR sebagai EOL untuk pemeriksaan whitespace, bukan mengubah bytes. Blob staged diverifikasi lagi terhadap manifest sebelum commit. [Hasil terkurasi](PROVENANCE_REVIEW.json) mencatat setiap hash dan perbandingan HEAD. `scripts/check-finance-context.py --staged` memeriksa24dokumen/98link, KF14/KNF8/U21 dan8hash index tanpa dependency tambahan.

## Aktif dan pengecualian

Context aktif diselaraskan dengan source FIN06 dan link IMPLEMENTATION_STATUS. Q10 angka dan remote GitHub dicatat sebagai keputusan pengguna; solver, bisnis, UAT/pentest dan gate akademik tidak otomatis disahkan. Schema/rute yang belum ada tetap diberi status rancangan/TODO. Core operasional tidak dipresentasikan sebagai seluruh aplikasi keuangan selesai.

Folder desain `docs/design/proposal-revisi-20261026/` tidak dipublikasi karena provenance belum ditinjau. PDF proposal, temp ekstraksi/render, .env, credentials, logs, APK/build/runtime dan database dump tidak di-stage. Pemindaian pola token/private-key berkeyakinan tinggi pada dokumen arsip/migrasi tidak menemukan kecocokan; ini bukan pentest atau bukti tidak ada seluruh jenis rahasia. Diff aktif dan daftar staging ditinjau. Link aktif/fence/requirement coverage serta archive blob hashes dicek; relative links historis di dalam archive memang mempertahankan path asli.

FIN00 document check lama melaporkan8/8 pada waktunya; pemeriksaan baru menemukan/memperbaiki README seperti di atas. Hasil lama tidak ditimpa untuk menyembunyikan perbedaan. Tidak ada reset/stash atau penghapusan kerja pengguna; teks indeks tetap tersimpan, lima context modified utuh.
