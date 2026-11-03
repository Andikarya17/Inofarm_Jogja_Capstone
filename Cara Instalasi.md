# Instalasi Inofarm

Produk aktif hanya Android Kotlin/Compose + Laravel API v2 + MySQL. Build tidak memerlukan firmware, PlatformIO atau npm, dan langsung membuka aplikasi pencatatan tanpa flag.

Ikuti [panduan instalasi/run FIN17](docs/migration/finance-20261029/FIN17_INSTALL_RUN.md) untuk environment server, akun pekerja/pengelola, assignment, SDK/JDK, build, update APK dan pengujian terisolasi. [Status/evidence](docs/context/IMPLEMENTATION_STATUS.md) mencatat hasil nyata; [cutover/rollback](docs/migration/finance-20261029/FIN17_CUTOVER.md) menjelaskan gate produksi.

Debug/profile tetap com.inofarm.jogja.finance, release tetap com.inofarm.jogja, minSdk29, versionCode2. Install -r mempertahankan SQLite9/draf/outbox/vault; jangan uninstall atau clear data. Pengukuran EC/PPM memerlukan API/schema15 additive. Jangan menjalankan migrate:fresh/reset/seed pada data pengguna; migration produksi, backup dan rekonsiliasi perlu rencana yang sesuai. Tidak ada auto-konversi tank/event menjadi greenhouse/aktivitas manusia.
