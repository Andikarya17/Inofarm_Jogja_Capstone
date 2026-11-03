# Inofarm — sistem visual Android

Arahan pengguna 3 November 2026: Liquid Glass berwarna dengan fondasi interaksi Material 3. Mode permukaan aplikasi: Operate.

## Implementasi

`core/design/InofarmTheme.kt` menyediakan peran Material light/dark, tipografi sans dalam sp dan skala spacing/shape. Hijau kebun menandai aksi dan konteks; netral hangat untuk konten. Status error memakai peran error, bukan hijau. Tidak ada dynamic color agar identitas dan semantic palette tetap konsisten.

`FarmChrome.kt` menyediakan chrome tonal kaca: highlight tepi, tint lembut dan kedalaman pada header/navigasi. Ini fallback solid yang sengaja tidak melakukan backdrop blur/refraction. Konten, angka dan field tetap stabil. Tidak ada dependensi blur, shader, atau klaim efek refraksi iOS.

Field memakai permukaan solid, label Material dan error existing; pemilih menonjolkan nilai di bawah label. Heading halaman memakai satu komponen bersama. Ikon navigasi digambar dengan stroke konsisten; label navigasi menyediakan nama aksesibel. EC/PPM memakai dua permukaan tonal terpisah, satuan dan status freshness dipertahankan.

## Aturan pengembangan

Pertahankan semantics/test tags dan state ownership. Touch target minimal 48 dp. Layout mengikuti inset sistem dan IME, font scale harus dapat tumbuh. Light/dark mengikuti sistem. Kaca hanya pada chrome; angka keuangan dan pesan status tidak diburamkan. Seluruh raw input, draft, offline/sync, role dan kontrak domain tetap menjadi batas desain.

## Batas

Dokumen ini menjelaskan implementasi tonal, bukan jaminan semua acceptance frontend telah lulus. Bukti run dan keterbatasan pengujian dicatat pada context/evidence frontend.
