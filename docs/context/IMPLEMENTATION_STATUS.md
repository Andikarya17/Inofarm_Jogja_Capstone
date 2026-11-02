# Status implementasi nyata — 29 Oktober 2026

Dokumen ini melengkapi context pack lokal FIN00 yang belum di-commit; tidak menyatakan seluruh target sudah tersedia. Baseline executable `9da5b30`. Perubahan dokumentasi pengguna sebelum implementasi tetap dipertahankan dan tidak disertakan dalam commit source baru.

| Task | Status | Bukti / batas |
|---|---|---|
| FIN01 core | PASS | Kontrak auth/context/activity/sync, decimal dan canonical hash PHP/Kotlin; [evidence](../migration/finance-20261029/evidence/FIN01.md). Kontrak finance/admin/upload/recovery lanjut per modul; FIN01 keseluruhan PARTIAL. |
| FIN02 backend | PASS / PARTIAL fitur | Auth/token pekerja-pengelola, public UUID, assignment-window scope dan bounded GH reads; [evidence](../migration/finance-20261029/evidence/FIN02.md). UI login/persistence sesi baru dan administrasi akun belum tersedia. |
| FIN03 backend | PASS / PARTIAL fitur | Master/assignment/user admin scoped, additive migrations, audit/version/receipt atomic; [evidence](../migration/finance-20261029/evidence/FIN03.md). UI master belum tersedia. |
| FIN04–17 | TODO | Belum tersedia pada commit FIN03. APK masih legacy. |

Q10: pengguna menyetujui IDR, DECIMAL(20,2) nominal, DECIMAL(20,6) kuantitas/harga dan HALF_UP pada total transaksi dalam chat pelaksanaan. Usulan solver dan business gates Q01–09/Q11–13 tidak otomatis disahkan.

Git: branch per task, dependency commit dicatat. Pengguna mengoreksi tujuan publikasi menjadi **GitHub**, remote `github`; gunakan itu untuk branch selanjutnya. FIN01 sempat dipush GitLab sebelum koreksi, lalu juga diterbitkan ke GitHub. Jangan mengganti URL remote atau menghapus branch lama tanpa instruksi. `origin/main` tidak punya common ancestor dengan snapshot lokal; tidak merge/rebase/force-push untuk mengatasi keadaan itu.
