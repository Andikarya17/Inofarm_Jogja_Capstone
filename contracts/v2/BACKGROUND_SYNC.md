# Sinkronisasi terjadwal dan profil — FIN16

Dependency `8f959f8` administrasi di atas FIN15 `e0dc9ac`; branch `codex/finance-background-sync`. Tanpa library/dependency/schema SQL/SQLite baru.

`GET /sync/status` tetap mengembalikan `schema_version` dan `sync_epoch`, ditambah `user {id,name,role,version}` publik. `ScopedRead` memeriksa token dan current actor di bawah lock ledger yang sama sebelum membaca epoch/profil. Tidak ada token/email/password/bigint internal dalam respons. OpenAPI `SyncStatusPublic` executable. Android memeriksa UUID profil sama dengan akun sesi sebelum initialize/pull/send; versi API tak sesuai menahan pengiriman. Server lama tanpa profil masih dapat dipakai, tetapi tidak memberikan pembaruan profil. Perubahan peran via user admin tetap mencabut token dan memerlukan reauth.

Vault menerbitkan profil aktif melalui StateFlow. Compose membangun ulang batas peran dan context/repository bind memakai peran terbaru; cache keuangan dibersihkan dari akses worker dan operasi pengelola ditahan oleh LocalFinanceStore existing. Credentials dan draft/outbox akun sebelumnya tetap terisolasi. Job tidak mengganti akun/re-ID/rebase atau melepaskan epoch hold.

`FinanceSyncSchedule` memasang satu JobScheduler periodic/network-any/persisted, ID1601, interval minimum15menit, setelah login/restore sesi finance. Tidak ada credential atau payload dalam JobInfo; JobService memulihkan vault dan memanggil coordinator yang sama, memakai mutex/durable outbox/backoff/role/scope/epoch existing. Token expired/logout tidak mengirim antrean. OS stop membatalkan coroutine/HTTP; pekerjaan dapat dijadwalkan kembali. Service dilindungi `BIND_JOB_SERVICE`; izin network state/boot ditambahkan. Legacy launcher tidak menjadwalkan job.

Penjadwalan Android dipengaruhi Doze, kuota, izin jaringan dan force-stop; interval bukan janji sinkronisasi tepat setiap15menit. Pengguna tetap dapat memakai **Sinkronkan**. Admin journal berpassword hanya dikirim melalui review/retry eksplisit; tidak diproses job operasional. Job yang ditolak OS tidak menggagalkan login. Tidak ada backup otomatis/retensi baru.

Rujukan implementasi: [JobScheduler](https://developer.android.com/reference/android/app/job/JobScheduler), [JobInfo.Builder](https://developer.android.com/reference/android/app/job/JobInfo.Builder), [JobService](https://developer.android.com/reference/android/app/job/JobService). Device assertions memeriksa konfigurasi/registration; waktu dispatch OS setelah reboot/Doze/perangkat fisik memerlukan observasi terpisah dan tidak disamakan dengan unit test.
