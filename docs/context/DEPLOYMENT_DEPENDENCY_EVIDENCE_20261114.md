# Bukti fase D2: patch dependency backend

Tanggal 14 November 2026. Branch `codex/deploy-dependency-patch`, bertumpuk di atas `codex/deploy-migration-guard`. Rujukan: [audit deployment](DEPLOYMENT_READINESS_AUDIT_20261114.md) temuan P0-4, [bukti D1](DEPLOYMENT_MIGRATION_EVIDENCE_20261114.md).

## Yang dijalankan (diuji)

Perintah, dengan PHP 8.5.11 dan Composer 2.10.3 dari `.tools`:

```
composer update "symfony/*" laravel/framework --with-dependencies
```

| Pemeriksaan | Sebelum | Sesudah |
|---|---|---|
| `composer audit --locked` | 7 advisory pada 4 paket (exit 1) | 0 advisory (exit 0) |
| Operasi lock | | 36 pembaruan, 1 paket baru, 0 dihapus |
| Guard database uji | 4 tes, 12 assertion | sama |
| Backend PHPUnit, hanya `inofarm_test` | 159 tes, 3.983 assertion | 159 tes, 3.983 assertion, 0 gagal |
| `check-finance-contracts.py` | lulus | lulus |
| `php artisan --version` | 13.35.0 | 13.42.0 |

Hanya `backend/composer.lock` yang berubah. `composer.json` tidak diubah, jadi semua pembaruan berada di dalam constraint yang sudah ada. Tidak ada kenaikan versi major. `dompdf/dompdf` (dikunci 3.1.6) dan `laravel/sanctum` tidak berubah.

## Advisory yang tertutup

| Paket | Advisory | Versi perbaikan | Terpasang |
|---|---|---|---|
| `symfony/http-foundation` | CVE-2026-121619 (trusted proxy `private_ranges`), CVE-2026-118514 (HEAD melewati aturan method), bypass aturan path lewat nama skrip, URL dengan host pilihan klien | 8.1.11 | v8.1.11 |
| `symfony/http-kernel` | CVE-2026-123339 (forwarded header pada inline fragment) | 8.1.11 | v8.1.11 |
| `symfony/routing` | CVE-2026-118516 (placeholder host cocok dengan awal path) | 8.1.11 | v8.1.11 |
| `laravel/framework` | CVE-2026-128083 (low, task output injection pada Concurrency) | 13.40.0 | v13.42.0 |

## Versi sebelum dan sesudah

Paket selain Symfony dan Laravel ikut naik karena `--with-dependencies` memperbarui dependency milik `laravel/framework`.

| Paket | Sebelum | Sesudah |
|---|---|---|
| `doctrine/inflector` | 2.1.0 | 2.1.1 |
| `dragonmantank/cron-expression` | v3.6.0 | v3.6.1 |
| `guzzlehttp/guzzle` | 8.2.0 | 8.5.0 |
| `guzzlehttp/promises` | 3.0.2 | 3.1.0 |
| `guzzlehttp/psr7` | 3.1.0 | 3.4.0 |
| `laravel/framework` | v13.35.0 | v13.42.0 |
| `laravel/prompts` | v0.3.25 | v0.3.27 |
| `laravel/serializable-closure` | v2.1.0 | v2.2.0 |
| `league/commonmark` | 2.10.3 | 2.10.7 |
| `league/uri` | 7.8.1 | 7.10.0 |
| `league/uri-interfaces` | 7.8.1 | 7.10.0 |
| `nesbot/carbon` | 3.14.2 | 3.15.1 |
| `nette/schema` | v1.3.6 | v1.4.0 |
| `nette/utils` | v4.1.5 | v4.1.6 |
| `nunomaduro/termwind` | v2.4.0 | v2.4.1 |
| `symfony/clock` | v8.1.0 | v8.1.9 |
| `symfony/console` | v8.1.8 | v8.1.11 |
| `symfony/css-selector` | v8.1.6 | v8.1.10 |
| `symfony/deprecation-contracts` | v3.7.1 | v3.8.1 |
| `symfony/error-handler` | v8.1.8 | v8.1.10 |
| `symfony/event-dispatcher-contracts` | v3.7.1 | v3.8.1 |
| `symfony/finder` | v8.1.8 | v8.1.11 |
| `symfony/http-foundation` | v8.1.8 | v8.1.11 |
| `symfony/http-kernel` | v8.1.8 | v8.1.11 |
| `symfony/mailer` | v8.1.7 | v8.1.10 |
| `symfony/polyfill-intl-grapheme` | v1.43.0 | v1.43.3 |
| `symfony/polyfill-mbstring` | v1.43.0 | v1.43.3 |
| `symfony/process` | v8.1.7 | v8.1.10 |
| `symfony/routing` | v8.1.8 | v8.1.11 |
| `symfony/service-contracts` | v3.7.3 | v3.8.1 |
| `symfony/string` | v8.1.7 | v8.1.10 |
| `symfony/translation` | v8.1.5 | v8.1.11 |
| `symfony/translation-contracts` | v3.7.1 | v3.8.1 |
| `symfony/uid` | v8.1.8 | v8.1.9 |
| `symfony/var-dumper` | v8.1.7 | v8.1.10 |
| `vlucas/phpdotenv` | v5.7.0 | v5.8.0 |
| `symfony/polyfill-time` | (baru) | v1.42.0 |

## Belum dilakukan atau belum diverifikasi

- **TrustProxies belum disetel.** Advisory proxy sudah ditambal di library, tetapi konfigurasi proxy tepercaya bergantung pada reverse proxy server pilot. Itu masuk contoh env fase D3 dan runbook D5.
- **Tidak ada uji manual lewat HTTP atau aplikasi Android** setelah pembaruan; buktinya suite PHPUnit dan pemeriksa kontrak.
- **Ekspor PDF tidak diuji manual.** Dompdf tidak berubah dan tes laporan di suite lulus.
- `composer validate` memberi satu peringatan lama: constraint `dompdf/dompdf` ditulis versi pasti. Itu disengaja dan tidak diubah.
