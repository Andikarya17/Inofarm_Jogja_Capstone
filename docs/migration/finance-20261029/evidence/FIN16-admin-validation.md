# FIN16 — perbaikan kontrak editor dari regresi

2 November 2026 WIB, parent `fd8be3b`, branch `codex/finance-admin-validation`.

Review menemukan list greenhouse API mengirim boolean SQL sebagai0/1, sedangkan form update memerlukan boolean. AdminForm kini menormalisasi0/1 dan true/false, tanpa mengubah server atau default active. Tambahan pemeriksaan lokal UUID parent, panjang teks/email, urutan tanggal/masa berlaku dan pasangan satuan PPM/pH menahan kesalahan sebelum HTTP. Empty tetap null dan0 tetap0. Filter greenhouse pada daftar jadwal/target kini memeriksa kelompok dari GH terpilih sambil mempertahankan cursor server; data GH lain tidak ditampilkan sebagai hasil filter tersebut. Server tetap sumber otorisasi/validasi akhir.

- Assemble app/test + JVM + lint **PASS/77 JVM/0 errors**,41s, `tmp/fin16-admin-validation-build.log`.
- Focused device **8 PASS/8.808s**, `tmp/fin16-admin-validation-device.log`: enam repository +dua UI. Pengujian memakai viewport aktual **720×1280/density360 (320dp)**, font200% pada kasus Back/resume. Ukuran/density awal dipulihkan dalam finally. Run sebelumnya pada viewport default emulator dan66case cross-feature tetap bukti terpisah.
- Tidak ada perubahan migration/ledger/matematika/recovery/dependency. Tidak mengulang full backend yang sudah134/2137PASS karena perubahan hanya editor Android. Bukan klaim UAT/physical/API29 baru.
