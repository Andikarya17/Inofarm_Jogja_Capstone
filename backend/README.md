# Inofarm Jogja API

Panduan ada di [README root](../README.md) dan [API.md](../docs/implementation/API.md). Laravel/PHP, Sanctum, MySQL; dependency dipin oleh composer.lock. Tidak ada frontend produksi di folder ini.

DatabaseSeeder tidak membuat akun, roster, jadwal, fase atau threshold produksi. Provisioning lewat artisan inofarm:owner/inofarm:device. Unit/feature tests menolak schema selain inofarm_test sebelum RefreshDatabase.