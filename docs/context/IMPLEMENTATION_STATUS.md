# Status implementasi nyata — 29 Oktober 2026

Dokumen ini melengkapi context pack lokal FIN00 yang belum di-commit; tidak menyatakan seluruh target sudah tersedia. Baseline executable `9da5b30`. Perubahan dokumentasi pengguna sebelum implementasi tetap dipertahankan dan tidak disertakan dalam commit source baru.

| Task | Status | Bukti / batas |
|---|---|---|
| FIN01 core | PASS | Kontrak auth/context/activity/sync, decimal dan canonical hash PHP/Kotlin; [evidence](../migration/finance-20261029/evidence/FIN01.md). Kontrak finance/admin/upload/recovery lanjut per modul; FIN01 keseluruhan PARTIAL. |
| FIN02 backend + login Android | PASS / PARTIAL fitur | Auth/token pekerja-pengelola, public UUID, assignment scope; UI/vault dibuktikan FIN06. [Server evidence](../migration/finance-20261029/evidence/FIN02.md). UI administrasi akun masih berikutnya. |
| FIN03 backend | PASS / PARTIAL fitur | Master/assignment/user admin scoped, additive migrations, audit/version/receipt atomic; [evidence](../migration/finance-20261029/evidence/FIN03.md). UI master belum tersedia. |
| FIN04 backend/domain/read UI | PASS / PARTIAL fitur | Jadwal/target versioned, WIB age PHP/Kotlin, comparator + immutable activity snapshots; [evidence](../migration/finance-20261029/evidence/FIN04.md). Editor acuan pengelola masih berikutnya. |
| FIN05 server | PASS core | Activity mutation/receipt/snapshot/audit dan scoped stable pull; [evidence](../migration/finance-20261029/evidence/FIN05-server.md). Terhubung Android pada FIN06. |
| FIN05 local core | PASS / PARTIAL fitur | Encrypted scoped drafts/outbox, ACK/version/epoch guards, foreground sync; [evidence](../migration/finance-20261029/evidence/FIN05-local.md). Restore reconciliation/background scheduling belum tersedia. |
| FIN06 core entry slice | PASS / PARTIAL acceptance | Login/context/forms/review/queue/history native, device offline→restart→real Laravel/MySQL→manager history; [evidence](../migration/finance-20261029/evidence/FIN06.md). Mitra/UAT, API29 runtime dan UI performance end-to-end belum disahkan/diukur. |
| FIN07–17 | TODO | Koreksi backend/entry sudah tersedia; UI conflict/cancel/audit, harvest/finance/investment/report/recovery dan admin master UI belum lengkap. |

Q10: pengguna menyetujui IDR, DECIMAL(20,2) nominal, DECIMAL(20,6) kuantitas/harga dan HALF_UP pada total transaksi dalam chat pelaksanaan. Usulan solver dan business gates Q01–09/Q11–13 tidak otomatis disahkan.

Git: branch per task, dependency commit dicatat. Pengguna mengoreksi tujuan publikasi menjadi **GitHub**, remote `github`; gunakan itu untuk branch selanjutnya. FIN01 sempat dipush GitLab sebelum koreksi, lalu juga diterbitkan ke GitHub. Jangan mengganti URL remote atau menghapus branch lama tanpa instruksi. `origin/main` tidak punya common ancestor dengan snapshot lokal; tidak merge/rebase/force-push untuk mengatasi keadaan itu.
