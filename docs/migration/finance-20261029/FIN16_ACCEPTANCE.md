# FIN16 — integrasi dan penerimaan

Status2 November2026: **engineering lokal PASS, penerimaan produk PARTIAL**. Baseline Cruelasss FIN11–15 `e0dc9ac`; lanjutan admin `8f959f8`, background/profile `fd8be3b`, validation `1fb1b39`, security `fec7ff1`. Seluruh branch bertumpuk; main tidak diubah. Gate eksternal tidak diganti dengan pengujian agen.

## Hasil baru

| Check | Hasil aktual | Bukti |
|---|---|---|
| Backend regresi |134 tests/2137 assertions PASS sebelum patch MIME; dua DemoHistory legacy dikecualikan eksplisit |[Profil/sync](evidence/FIN16-sync.md) |
| Patch dependency |Hanya symfony/mime8.1.7→8.1.10;25 tests/416 assertions PASS sesudah patch; platform PASS; audit0 |[Security](evidence/FIN16-security-dependency.md) |
| Android app/test/JVM/lint |77 JVM PASS;0 lint errors/34 warnings/5 hints |[Validasi editor](evidence/FIN16-admin-validation.md) |
| Regresi perangkat |66 PASS/84.892s lintas admin/store/sync/recovery/investment/season/export/performance; sesudah validasi editor8 focused PASS/8.808s |[Sync](evidence/FIN16-sync.md), [editor](evidence/FIN16-admin-validation.md) |
| Dua viewport |Default API37/5554 dan320dp; font200%/scroll/Back/resume diuji pada admin |[Editor](evidence/FIN16-admin-validation.md) |
| Simpan lokal |1.000 encrypted synthetic records,30 saves,11.8686–22.7395ms,mean17.28978ms,0>2s |[CSV aktual](evidence/data/FIN16-local-storage-timings.csv) |
| Integrasi API/MySQL |1 test/52 assertions/5.247s PASS: admin akun→worker login→manual activity/replay+harvest→manager payment/allocation/sale→evaluation→snapshot CSV→sale cancel→snapshot tetap→worker denial/feed |`FinanceIntegrationFlowTest`, `outputs/finance-readiness/20261102-191220-5499386d` privat |
| Reproducible runner |Contract/context/DB guard/backend integration/dependency audit PASS; build/device sengaja SKIPPED pada pemanggilan runner karena sudah diperiksa terpisah |[Runner](../../../scripts/test-finance-readiness.ps1) |

Angka dari run terpisah tidak dijumlahkan sebagai satu suite bersih. Storage timing bukan end-to-end UI latency. Transport sintetis/device bukan bukti MySQL; feature tests memakai MySQL nyata tetapi bukan proses Android→HTTP service. Bukti real-device/reboot/concurrency/restore FIN11–15 dipertahankan sebagai **historis**, tidak dicap run baru.

## U01–21: cakupan dan sisa acceptance

| ID | Coverage yang tersedia | Sisa penerimaan |
|---|---|---|
| U-01 |Auth/vault/logout regression, worker login real API pada integration |UAT login/logout kedua peran |
| U-02 |Direct worker finance/report/file denial; scoped backend regression |Pentest berizin |
| U-03 |Master API MySQL +native admin/draft/retry/version/active |Real-device seluruh editor, UAT admin |
| U-04 |Backend forms/domain, raw/native drafts; extra admin validation |UAT field/unit tiap jenis |
| U-05 |PlantAge PHP/JVM existing, WIB calendar fixtures |UAT tanggal per kelompok |
| U-06 |Reference versions/comparison snapshot regression |UAT target/stadia aktual |
| U-07 |Cancel/close/reopen/version/audit existing; native admin transitions |UAT late queue/reopen |
| U-08 |Optimistic versions/atomicity backend; races historis FIN07–13 |Dua perangkat fisik simultan |
| U-09 |Expense/allocation/cap/depreciation backend; integration TC80 |Race proofs historis, UAT basis alokasi |
| U-10 |Harvest→paid sale→cancel balance integration +backend guard regression |UAT penjualan bertahap |
| U-11 |Assets/depreciation/cap/duplicate usage regression |UAT periode/basis eksplisit |
| U-12 |Exact season math/report regression; TC80/TR150/L70 flow |Acceptance data usaha mitra |
| U-13 |Investment exact math/fixtures/oracle historis FIN13; native regression |Persetujuan toleransi/convention Q03/Q10/Q11 |
| U-14 |Invalid/missing/nonconventional/solver statuses regression |UAT alasan/status |
| U-15 |Frozen CSV integration setelah cancel; PDF/CSV regression sesudah MIME patch |UAT PDF/CSV pengguna |
| U-16 |LocalStore/sync/admin reopen; real offline+OS reboot FIN11–15 historis |Full flow baru lintas modul pada fisik |
| U-17 |Identical API replay integration; response-loss native repository regression |Fault injection full device flow tetap historis |
| U-18 |Current profile demotion/foreign identity/session fences +backend denial |Revoke/role multi-perangkat dan job runtime |
| U-19 |FIN15 actual isolated MySQL53tables/123rows/73FK+file+epoch historis; recovery regression baru |RPO/RTO/retensi/operator produksi Q08 |
| U-20 |320dp/default/font200%,1.000rows/30samples |End-to-end UI timing, landscape lintas fitur, fisik/API29 run baru |
| U-21 |Upload/export/access/injection regression, MIME patch +audit0 |Pentest eksplisit berizin dengan scope/PIC/hasil |

## KNF01–08

| ID | Status lokal | Gate terbuka |
|---|---|---|
| KNF-01 |UI Indonesia/unit/reasons; editor+flow regression |Tugas kedua peran bersama mitra |
| KNF-02 |30/30 storage saves≤2s pada1.000records |Latensi dari aksi UI hingga feedback pada perangkat sasaran |
| KNF-03 |Durable draft/outbox/reopen +historical actual reboot |Device fleet baru, admin real process-death bersama API |
| KNF-04 |Scope/token/private files/AES-GCM/HTTPS release guard; audit0 |HTTPS deploy/config review dan pentest |
| KNF-05 |Replay integration +immutable retry native |Background dispatch nyata sesudah Doze/reboot |
| KNF-06 |Fixed decimal dan math oracle historis; actual API finance totals baru |Toleransi non-nominal/asumsi mitra |
| KNF-07 |Isolated restore nyata FIN15 historis +regresi recovery baru |Produksi capacity/RPO/RTO/key escrow |
| KNF-08 |minSdk29,API37 baru,320dp/default,font200% |Fisik/Android10 run baru dan seluruh landscape |

## Menjalankan seperlunya

Siapkan `.env.testing` dengan effective MySQL **inofarm_test** dan akun privilege schema itu saja. Runner menolak manifest fixture/HTTP8001 aktif sebelum PHPUnit. Jangan PHPUnit bersamaan dengan runner real-device/recovery. Tanpa `-Device`, instrumentation ditulis SKIPPED; `-SkipBuild/-SkipBackend` tidak menghasilkan klaim PASS atas langkah yang dilewati.

```powershell
./scripts/test-finance-readiness.ps1 -JavaHome $env:JAVA_HOME
# Setelah APK/checks sebelumnya valid; pilih perangkat uji secara eksplisit:
./scripts/test-finance-readiness.ps1 -JavaHome $env:JAVA_HOME -SkipBuild -SkipBackend -Device emulator-5554
# Follow-up terarah, bukan regresi lengkap:
./scripts/test-finance-readiness.ps1 -SkipBuild -BackendFilter FinanceIntegrationFlowTest -AuditDependencies
```

Runner tidak menghapus app-data/reset database pengguna, menjalankan production restore, atau menganggap pengujian keamanan regresi sebagai pentest. Output/log/credentials/packages tetap ignored. Persetujuan mitra, pembimbing, PIC/tanggal, dan keputusan Q07/Q08/Q11–13 harus ditambahkan ke dokumen gate FIN17 sebelum handover resmi.
