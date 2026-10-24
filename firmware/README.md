# Firmware Inofarm Jogja

Lihat [panduan root](../README.md) dan [API.md](../docs/implementation/API.md). ESP32/Arduino PlatformIO sesuai BOM: DS18B20, PH4502C, DHT22, DS3231, LCD20x4, tombol, LED/buzzer dan MicroSD.

esp32dev tanpa wiring membangun core/headless tanpa data sensor palsu. Native menguji menu, ACK dan batas waktu. esp32_compile_check memeriksa syntax/link adapter dengan pin kosong; **jangan flash binary check**. Compile bukan bukti operasi fisik. Tidak ada TDS atau dosing.

## Transport dan queue setelah T3

Build operasional memakai HTTPS dengan CA. HTTP default ditolak; hanya bench lokal dengan `INOFARM_ALLOW_HTTP_BENCH=1` eksplisit dan IPv4 privat/loopback kanonis. Host DNS/prefix palsu, userinfo, octet berawalan nol/invalid dan port invalid ditolak. Jangan memasukkan secrets ke contoh/Git.

Worker FreeRTOS menjalankan HTTP (connect timeout 5 detik/read 10 detik); loop utama tetap memproses tombol, alarm deadline, SD dan konfigurasi. Pengurasan event terpisah dari polling config 30 detik, memilih kandidat secara circular/fair. Timeout/429/503 dapat retry; malformed/conflict/rejected event menyimpan `.blocked` dan payload tetap ada. Marker `.ack` hanya setelah persisted ACK dengan event ID yang cocok dan penyimpanan lokal berhasil. Tidak ada auto-deletion/retention atau rewrite UUID/body. Payload blocked memerlukan review penyebab; jangan otomatis membuang catatan untuk membuka antrean.

Native8 test dan dua ESP32 build lulus menurut [VERIFICATION](../docs/implementation/VERIFICATION.md). Ini tidak membuktikan latency SD/sensor, tombol/alarm board, TLS greenhouse atau SD power-loss; semua tetap NOT_VERIFIABLE fisik. Tidak ada flashing.
