# Commit dan push GitLab UMY

Tanggal: 15 Oktober 2026, Asia/Jakarta. Task ini diotorisasi pengguna untuk memublikasikan snapshot source existing dalam commit berdasarkan fitur. Author: Andika Arya Putra <andika.arya.ft23@mail.umy.ac.id>.

Remote: https://student-git.umy.ac.id/andika.arya.ft23/inofarm_jogja.git

Sumber SOP: `Aturan Main/SOP GitLab UMY v2.pdf`, bagian 2â€“4,6â€“8. Riwayat remote awal main memiliki tiga commit, terakhir `9ef1363`; README template diganti panduan proyek, konfigurasi security CI dipertahankan. Index awal lokal disalin ke `.git/index.before-feature-commits` sebelum pengelompokan; file kerja tidak dihapus.

## Pembagian

Commit dipisahkan menjadi aturan repository/akademik; konteks dan kontrak; prototipe React; fondasi Laravel/MySQL; autentikasi/provisioning; konfigurasi/fase/validasi B; pekerja/shift historis; ingestion idempotent; dashboard/riwayat/CSV; tes backend; fondasi dan data Android; UI bersama/navigasi; dashboard Android; riwayat/filter Android; pengaturan Android; pekerja/shift Android; regression/performance tests Android; firmware; tooling lokal; dan dokumentasi verifikasi. Shared files ditempatkan pada fondasi terkait. Seluruh rangkaian merupakan satu snapshot impor dengan dependency antarfitur; hasil build/test yang dilaporkan berlaku pada snapshot lengkap, bukan klaim setiap commit antara telah diuji terpisah. Tanggal commit adalah tanggal publikasi aktual, tanpa backdating.

## Prosedur

1. Fetch origin, periksa riwayat remote dan gunakan sebagai parent branch kerja `codex/project-import`.
2. Periksa file kandidat, abaikan secrets/environment/runtime/cache IDE/log/build outputs; gunakan template kosong untuk setup. Fixture TLS androidTest khusus tes, tidak ada di APK produksi.
3. Jalankan pemeriksaan lokal; stage file terpilih dan periksa diff setiap commit.
4. Commit dengan pesan konsisten `type(scope): ringkasan`. Fetch ulang sebelum push; jika main remote berubah, integrasikan di branch kerja dan validasi ulang.
5. Perbarui main dengan fast-forward dari branch kerja. Push branch kerja dan main dengan upstream, tanpa force.
6. Bandingkan hash main lokal/remote, periksa status kerja dan pipeline GitLab yang dikonfigurasi. Pipeline yang berjalan/pending bukan PASS; ketersediaan runner dan hasil server dilaporkan terpisah.

## Hasil validasi lokal

Backend MySQL inofarm_test18 tests/247 assertions PASS, guard4/12 PASS, Composer strict/Pint PASS. Android assembleDebug/unit10/lint PASS (0 errors,28 warnings,4 hints; mayoritas task UP-TO-DATE). Firmware native8/8, headless dan adapter compile-check PASS. Tidak ada ADB device saat sesi ini; instrumentation tidak diulang. React reference dependencies belum terpasang; build/lint React tidak diulang. Detail termasuk percobaan awal database belum menyala di VERIFICATION.md.

Tidak ada deployment/flashing, data owner tidak di-reset, hardware/pentest/reviewer/handover tetap terbuka. Keberhasilan push dan hasil pipeline hanya boleh diklaim setelah remote diperiksa.

Pemeriksaan diff mempertahankan hard line breaks Markdown melalui .gitattributes. Blank line EOF dan trailing spaces bawaan source existing diperiksa terhadap hash snapshot awal dan tidak diubah agar source referensi/implementasi tetap sama; conflict marker atau perubahan whitespace baru tetap menghentikan proses.
