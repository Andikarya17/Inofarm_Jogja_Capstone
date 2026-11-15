# Runbook deployment pilot Inofarm

Disusun 15 November 2026 untuk uji coba terbatas (pilot/UAT) dengan Inofarm. Keputusan pemilik G2: sesi ini hanya menyiapkan runbook, tidak men-deploy. Semua langkah ditulis untuk satu server Linux (Ubuntu 24.04 LTS atau setara) dengan Nginx, PHP-FPM dan MySQL 8 di mesin yang sama.

Tanda di setiap langkah:

- **Diuji** = dijalankan di laptop pengembang (Windows, MySQL 8.0.30, database `inofarm_test`) dengan hasil tercatat di bukti fase D.
- **Belum diuji** = belum pernah dijalankan di server Linux. Jalankan sekali di server staging sebelum mengundang Inofarm, lalu ubah tandanya di dokumen ini.

Rahasia (password database, `APP_KEY`, keystore, token) tidak pernah ditulis ke dokumen ini, chat, commit, atau tiket. Pemilik server mengetikkannya langsung di server.

## 1. Spesifikasi server

| Komponen | Kebutuhan | Dasar |
|---|---|---|
| PHP | **8.4.1 atau lebih baru** (8.4 atau 8.5), CLI dan FPM | `composer.lock` pada branch D2/D3: paket Symfony 8 meminta `php >=8.4.1`. Constraint `^8.3` di `composer.json` tidak cukup |
| Extension PHP | `bcmath`, `ctype`, `dom`, `fileinfo`, `filter`, `hash`, `iconv`, `json`, `libxml`, `mbstring`, `openssl`, `pcre`, `pdo_mysql`, `session`, `tokenizer`; disarankan `gd` untuk gambar di PDF | `composer check-platform-reqs --lock --no-dev` (diuji, semua lulus di PHP 8.5.11 laptop). `bcmath` dipakai langsung oleh kode domain (`bcadd`, `bccomp`) tetapi **tidak dideklarasikan** di `composer.json`, jadi pemeriksa Composer tidak akan menangkapnya bila hilang |
| Database | MySQL 8.0 (laptop memakai 8.0.30), `utf8mb4` | `DATABASE_INVENTORY_20261110.md` |
| Web | Nginx + PHP-FPM, document root `backend/public` | |
| TLS | Sertifikat Let's Encrypt (certbot) untuk domain pilot | APK rilis hanya menerima `https://` (`ApiBaseUrl.kt`) |
| Composer | 2.x | |
| Cron | Ada | Jadwal Laravel dan backup harian |
| Disk | Ruang untuk database, lampiran privat, dan 14 cadangan harian | Retensi pilot 14 hari (G5) |

Tidak dibutuhkan: Node.js, frontend web, Redis, worker antrean (`QUEUE_CONNECTION=sync`), firmware atau IoT.

## 2. Akun dan database

Belum diuji di server. Jalankan sebagai root MySQL oleh pemilik:

```sql
CREATE DATABASE inofarm_pilot CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'inofarm_app'@'127.0.0.1' IDENTIFIED BY '<diketik pemilik>';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, DROP, REFERENCES, LOCK TABLES, TRIGGER, SHOW VIEW ON inofarm_pilot.* TO 'inofarm_app'@'127.0.0.1';
FLUSH PRIVILEGES;
```

- Akun aplikasi hanya punya hak pada **satu** database. Ia tidak bisa membuat database lain; uji restore (bagian 8) memakai database kosong yang dibuat root.
- `DROP` dan `ALTER` dibutuhkan oleh migration Laravel; `TRIGGER`, `SHOW VIEW` dan `LOCK TABLES` dibutuhkan `mysqldump` pada skrip backup. Bila ingin lebih ketat, beri hak itu hanya saat migrate lalu cabut.
- Jangan memakai akun root untuk aplikasi.

## 3. Instalasi

Belum diuji di server.

```bash
sudo adduser --system --group --home /var/www/inofarm inofarm
sudo -u inofarm git clone https://github.com/Andikarya17/Inofarm_Jogja_Capstone.git /var/www/inofarm/app
cd /var/www/inofarm/app
sudo -u inofarm git switch <branch atau tag rilis pilot>
cd backend
sudo -u inofarm composer install --no-dev --prefer-dist --optimize-autoloader --no-interaction
sudo -u inofarm composer check-platform-reqs --lock --no-dev
php -m | grep -i bcmath   # wajib muncul; Composer tidak memeriksanya
```

Branch yang dipakai harus sudah memuat fase D1 sampai D3 (penjaga migration, patch dependency, hardening). Sampai keempat branch D di-merge ke `main`, `main` belum memuatnya.

## 4. `.env` produksi

Belum diuji di server. Contoh tanpa nilai rahasia ada di `backend/.env.production.example` (branch D3).

```bash
sudo -u inofarm cp .env.production.example .env
sudo -u inofarm chmod 600 .env
sudo -u inofarm nano .env          # isi APP_URL https, DB_*, FINANCE_RECOVERY_OPERATORS bila perlu
sudo -u inofarm php artisan key:generate
sudo -u inofarm php artisan config:cache
sudo -u inofarm php artisan route:cache
```

Nilai pilot yang sudah diputuskan pemilik (G4, G5) dan sudah tertulis di contoh:

| Kunci | Nilai pilot |
|---|---|
| `APP_ENV` / `APP_DEBUG` / `LOG_LEVEL` | `production` / `false` / `warning` |
| `FINANCE_SESSION_SECONDS` | `604800` (7 hari) |
| `FINANCE_ATTACHMENT_MAX_BYTES` / `_MAX_FILES` / `_MIME_TYPES` | `5242880` / `5` / `image/jpeg,image/png,application/pdf` |

**`APP_KEY` jangan pernah diganti** setelah ada data. Sesi dan data terenkripsi menjadi tidak terbaca. Simpan salinan `APP_KEY` di pengelola rahasia milik pemilik, terpisah dari cadangan database.

**TrustProxies:** Nginx dan PHP-FPM di mesin yang sama tidak butuh pengaturan. Bila ada proxy atau CDN di depan (misalnya Cloudflare), daftarkan alamat proxy itu lewat `$middleware->trustProxies(at: [...])` di `bootstrap/app.php`. Jangan memakai `'*'` kecuali server hanya bisa dijangkau lewat proxy itu. Patch Symfony D2 menutup advisory trusted proxy, tetapi pengaturan yang salah tetap membuat alamat dan skema klien bisa dipalsukan.

## 5. Migration

Database baru yang kosong (belum diuji di server):

```bash
cd /var/www/inofarm/app/backend
sudo -u inofarm php artisan migrate:status
sudo -u inofarm php artisan migrate --pretend      # baca SQL-nya
sudo -u inofarm php artisan migrate --force
sudo -u inofarm php artisan migrate:status --pending=1 && echo "schema bersih"
```

Database yang sudah berisi data: **backup dulu** (bagian 7), simpan hash-nya, baru jalankan urutan di atas.

`php artisan migrate:status --pending=1` keluar dengan kode 1 selama masih ada migration pending dan kode 0 bila bersih. **Diuji** di D1 dan dikunci tes. Nilai `=1` wajib ditulis: tanpa nilai, kode keluarnya tidak berubah. Jalankan perintah ini setelah setiap `migrate --force` dan sebelum membuka akses ke Inofarm. Kejadian P0-1 (sinkronisasi pekerja gagal karena migration 20 belum dijalankan) adalah alasan langkah ini.

Jangan menjalankan `migrate:fresh`, `migrate:reset`, `db:seed`, atau `scripts/local-database.ps1` di server. Tidak ada seed produksi: data awal diisi pengelola lewat aplikasi (bagian 11).

## 6. Web server, izin berkas dan TLS

Belum diuji di server.

```bash
sudo chown -R inofarm:www-data /var/www/inofarm/app/backend/storage /var/www/inofarm/app/backend/bootstrap/cache
sudo chmod -R ug+rwX /var/www/inofarm/app/backend/storage /var/www/inofarm/app/backend/bootstrap/cache
```

Contoh blok Nginx (`/etc/nginx/sites-available/inofarm`):

```nginx
server {
    listen 80;
    server_name <domain-pilot>;
    root /var/www/inofarm/app/backend/public;
    index index.php;
    client_max_body_size 6m;   # lampiran pilot 5 MB ditambah amplop multipart

    location / { try_files $uri $uri/ /index.php?$query_string; }
    location ~ \.php$ {
        include snippets/fastcgi-php.conf;
        fastcgi_pass unix:/run/php/php8.4-fpm.sock;
        fastcgi_param SCRIPT_FILENAME $realpath_root$fastcgi_script_name;
    }
    location ~ /\.(?!well-known) { deny all; }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/inofarm /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d <domain-pilot>
curl -s -o /dev/null -w '%{http_code}\n' https://<domain-pilot>/api/v2/me    # harus 401 tanpa token
```

Lampiran disimpan privat di `storage/app/private/finance-v2` (atau `FINANCE_PRIVATE_ROOT`), di luar `public`. Jangan menjalankan `php artisan storage:link` untuk folder itu.

## 7. Cron: jadwal Laravel dan backup harian

Belum diuji di server. `crontab -e` sebagai pengguna `inofarm`:

```cron
* * * * * cd /var/www/inofarm/app/backend && php artisan schedule:run >> /dev/null 2>&1
30 1 * * * /var/www/inofarm/app/scripts/backup-mysql.sh /var/backups/inofarm /var/www/inofarm/app/backend/.env >> /var/log/inofarm-backup.log 2>&1
```

- `schedule:run` menjalankan `sanctum:prune-expired --hours=24` setiap hari (D3; terdaftar di `schedule:list`, **diuji** di laptop tanpa cron).
- `scripts/backup-mysql.sh` (D3) membaca `DB_*` dari `.env` yang ditunjuk, menulis dump bertanggal dan berkas `.sha256`, menghapus dump tanpa penanda "Dump completed", dan menghapus cadangan yang lebih tua dari `RETENTION_DAYS` (bawaan 14). Password lewat `MYSQL_PWD`, tidak lewat argumen proses. **Diuji** dengan Git Bash dan `mysqldump.exe` terhadap `inofarm_test`; **belum diuji** di Linux. Retensi belum terbukti karena tidak ada berkas tua.
- Folder `/var/backups/inofarm` milik `inofarm`, mode 700, di luar webroot.
- Nilai pilot Q08 (G5): backup harian, retensi 14 hari, RPO 24 jam, RTO 1 hari kerja, operator pemilik. Untuk RPO 24 jam, salin juga cadangan ke lokasi kedua (penyimpanan lain milik pemilik) secara terenkripsi; sesi ini tidak menyiapkan salinan luar server.

## 8. Uji restore (wajib sekali sebelum pilot)

Belum diuji: akun aplikasi tidak punya hak `CREATE DATABASE`, dan cadangan `inofarm_finance_local` dari D1 belum pernah dipulihkan. Pemilik menjalankan:

```bash
# 1. Root MySQL membuat database kosong khusus uji, lalu memberi hak pada akun aplikasi.
mysql -u root -p -e "CREATE DATABASE inofarm_restore_check CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; GRANT ALL ON inofarm_restore_check.* TO 'inofarm_app'@'127.0.0.1';"
# 2. Pilih cadangan terbaru dan cocokkan hash-nya.
cd /var/backups/inofarm && sha256sum -c inofarm_pilot-<tanggal>.sql.sha256
# 3. Pulihkan ke database uji, bukan ke database pilot.
mysql -u inofarm_app -p inofarm_restore_check < inofarm_pilot-<tanggal>.sql
# 4. Bandingkan jumlah tabel dan baris kunci.
mysql -u inofarm_app -p -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='inofarm_restore_check';"
mysql -u inofarm_app -p -e "SELECT (SELECT COUNT(*) FROM inofarm_pilot.users), (SELECT COUNT(*) FROM inofarm_restore_check.users);"
# 5. Hapus database uji setelah dicatat.
mysql -u root -p -e "DROP DATABASE inofarm_restore_check;"
```

Catat tanggal, hash, jumlah tabel (55 setelah migration 20) dan hasil perbandingan di dokumen bukti pilot.

## 9. Pemantauan log

Belum diuji di server.

- Laravel: `backend/storage/logs/laravel-<tanggal>.log` (`LOG_CHANNEL=daily`, `LOG_LEVEL=warning`). Periksa setiap hari selama pilot: `tail -n 100 storage/logs/laravel-$(date +%F).log`.
- Nginx: `/var/log/nginx/error.log`, dan `access.log` untuk lonjakan 5xx (`awk '$9 ~ /^5/' /var/log/nginx/access.log | tail`).
- Backup: `/var/log/inofarm-backup.log`. Satu baris path dan satu baris hash per hari; galat berarti cadangan hari itu tidak ada.
- Setiap respons galat API memuat `request_id`. Minta pengguna menyertakannya saat melapor, lalu cari di log Laravel.

## 10. Rilis berikutnya dan rollback

Belum diuji di server.

Rilis:

1. Backup (bagian 7) dan catat hash.
2. `git fetch && git switch --detach <tag rilis baru>`
3. `composer install --no-dev --optimize-autoloader`
4. `php artisan migrate --pretend`, lalu `php artisan migrate --force`, lalu `php artisan migrate:status --pending=1`.
5. `php artisan config:cache && php artisan route:cache`, lalu `sudo systemctl reload php8.4-fpm`.

Rollback:

- **Kode tanpa migration baru:** kembali ke tag sebelumnya, ulangi langkah 3 dan 5.
- **Rilis dengan migration baru:** migration proyek bersifat additive dan tidak punya jalur turun yang diuji. Jangan menjalankan `migrate:rollback` di database pilot. Pulihkan cadangan sebelum rilis ke database pilot hanya bila data pasca-rilis boleh hilang (sesuai RPO 24 jam), dengan persetujuan pemilik. Bila tidak boleh hilang, perbaiki maju.
- APK yang sudah terpasang tetap bekerja selama kontrak API v2 tidak berubah; outbox perangkat tersimpan sampai server kembali.

## 11. APK rilis

Belum dirakit dengan keystore asli; konfigurasinya ada di branch `codex/deploy-android-release` (D4).

1. Pemilik membuat keystore sekali, menyimpannya di luar repo, dan mencadangkannya bersama password di pengelola rahasia. Kehilangan keystore berarti pembaruan APK tidak bisa dipasang di atas versi lama.
2. Isi empat nilai di `%USERPROFILE%\.gradle\gradle.properties` (bukan di repo) atau sebagai variabel lingkungan: `INOFARM_RELEASE_STORE_FILE`, `INOFARM_RELEASE_STORE_PASSWORD`, `INOFARM_RELEASE_KEY_ALIAS`, `INOFARM_RELEASE_KEY_PASSWORD`.
3. Rakit: `./scripts/build-android.ps1 -Tasks ':app:assembleRelease','-PinofarmFinanceApiUrl=https://<domain-pilot>/api/v2/'`. Gradle menolak rilis bila URL bukan `https://…/api/v2/`, dan memperingatkan bila keystore belum diisi (APK keluar tanpa tanda tangan).
4. Naikkan `versionCode` setiap APK yang dibagikan.

## 12. Checklist sebelum mengundang Inofarm

- [ ] Branch D1 sampai D3 (dan F yang dipakai) sudah digabung ke rilis yang dipasang.
- [ ] `composer check-platform-reqs --lock --no-dev` lulus dan `php -m` memuat `bcmath`.
- [ ] `.env` produksi terisi, `APP_DEBUG=false`, `APP_URL` https, `APP_KEY` dibuat dan disalin ke pengelola rahasia pemilik.
- [ ] `php artisan migrate:status --pending=1` keluar 0.
- [ ] HTTPS aktif; `curl -H "Accept: application/json" https://<domain-pilot>/api/v2/me` tanpa token menjawab 401. Tanpa header itu backend saat ini menjawab 500 (`Route [login] not defined`, ditemukan 15 November 2026 di server uji lokal); aplikasi Android selalu mengirim header JSON sehingga tidak terdampak, tetapi pemantauan dan pemindai sebaiknya memakai header tersebut sampai backend diperbaiki.
- [ ] Cron `schedule:run` dan backup harian terpasang; satu cadangan pertama sudah ada beserta `.sha256`.
- [ ] Uji restore (bagian 8) sudah dilakukan dan dicatat.
- [ ] APK rilis bertanda tangan (D4) dirakit dengan `-PinofarmFinanceApiUrl=https://<domain-pilot>/api/v2/`, dipasang di satu ponsel fisik, dan alur login, Beranda, catat, kirim berhasil.
- [ ] Akun pengelola Inofarm dibuat; tidak ada akun contoh atau data sintetis di database pilot.
- [ ] Data awal diisi pengelola lewat aplikasi: greenhouse, musim, varietas, kelompok tanam dan "tanam saat ini", satuan, bahan, grade, kategori biaya, jadwal dan target, akun pekerja dan penugasan. Tanpa "tanam saat ini", alur "Cek nutrisi semua greenhouse" melewati greenhouse itu.
- [ ] Dry run dua ponsel (Q2) sudah dilakukan terhadap server ini.
- [ ] Kontak pelaporan galat dan cara menyertakan `request_id` disampaikan ke Inofarm.

## Rujukan

- [Audit kesiapan deployment](../context/DEPLOYMENT_READINESS_AUDIT_20261114.md)
- Bukti fase D1 sampai D3 di branch `codex/deploy-migration-guard`, `codex/deploy-dependency-patch`, `codex/deploy-backend-hardening`
- [Panduan instalasi lama FIN17](../migration/finance-20261029/FIN17_INSTALL_RUN.md) (histori; runbook ini menggantikannya untuk server pilot)
