# Impeccable — instalasi project 3 November 2026

Permintaan: memasang Impeccable dari https://impeccable.style/#downloads di repository ini.

- Lokasi Codex: `.agents/skills/impeccable/` (project-local, bukan instalasi global).
- Sumber: https://github.com/pbakaus/impeccable pada commit `9fadc41729c38fd47d7ae622b501558653f9d2e9`.
- Skill 4.8.0; launcher engine 0.1.18; termasuk referensi Android native.
- Instalasi melalui helper `install-skill-from-github.py --repo pbakaus/impeccable --ref 9fadc41729c38fd47d7ae622b501558653f9d2e9 --path .agents/skills/impeccable --dest .agents/skills`.
- Paket disalin manual melalui helper: hook otomatis tidak diaktifkan dan konfigurasi agent global tidak diubah.

## Penggunaan

Skill tersedia mulai giliran berikutnya; bila belum terdeteksi, buka ulang chat/project Codex. Contoh: `$impeccable critique layar dashboard Android` atau `$impeccable polish form pencatatan operasional`. Instruksi AGENTS.md dan context finance tetap menjadi acuan kebutuhan dan stack Kotlin/Compose.

## Verifikasi dan batas

63 file skill diperiksa terhadap tree Git upstream (launcher `.cmd` dinormalisasi ke CRLF oleh installer). Uji `impeccable.cmd engine-probe` gagal mengunduh engine Windows x64 dari GitHub Releases. Engine/detector belum terbukti berjalan; retry launcher ketika unduhan release tersedia. Tidak ada perubahan runtime Android/backend/database. Instalasi ini tidak menjalankan init, mendesain ulang UI, atau menghasilkan PRODUCT.md/DESIGN.md.
