# Prompt pembuka: tampilan login dan pengelola, arah Canopy

Buka sesi baru Claude Code di root repo dengan model **Sonnet 5.5**, lalu tempel seluruh teks di dalam blok berikut.

```text
Kamu akan memperbaiki tampilan Android Inofarm: layar login dan semua halaman pengelola, mengikuti arah visual "Canopy". Ini pekerjaan frontend saja (Kotlin + Jetpack Compose). Backend, kontrak API dan database tidak boleh berubah.

Sumber kebenaran tugas ini adalah docs/context/MASTER_PROMPT_FRONTEND_CANOPY.md. Baca seluruhnya, lalu docs/context/FRONTEND_CANOPY_AUDIT_20261111.md, CLAUDE.md, AGENTS.md, PRODUCT.md dan DESIGN.md. Jika ada yang bertentangan, keputusan K1–K8 di master prompt bagian 1.1 yang berlaku.

Pilihan skill dari saya untuk sesi ini:
- Use antislop during for this session. Ini session override; jangan tanyakan mode lagi dan jangan membuat file settings antislop.
- Muat skill dengan tool Skill, urut: antislop, antislop-ui, antislop-layoutmobile, antislop-human, antislop-copywriting, lalu impeccable. Muat antislop-code sebelum setiap commit.
- Jangan pakai skill ponytail atau turunannya untuk pekerjaan ini.
- Jalankan launcher context impeccable sekali (Windows: .claude/skills/impeccable/scripts/impeccable.cmd context --target android/app/src/main/java/com/inofarm/jogja/feature/FinanceRoot.kt), mode Operate, platform Android native. Baca reference/craft-floor.md tepat sebelum setiap edit UI.
- Pakai subagent impeccable-finish-reviewer di akhir Fase 2, 4, 5, 6, 7, 8 dan 9, dan impeccable-documenter di Fase 9.

Cara kerja:
1. Mulai dari Fase 0 tanpa mengubah kode. Laporkan: branch dan commit aktif, skill yang berhasil dimuat, hasil launcher impeccable, matriks requirement -> acceptance -> file -> tes, daftar tes yang memakai tag yang akan dipindah, perbedaan fakta master prompt bagian 3 dengan HEAD, dan screenshot "sebelum" dari fixture.
2. Lanjutkan Fase 1 sampai 9 berurutan, satu branch codex/ per fase. Cakupannya login, Beranda, detail greenhouse, sheet Catat, Riwayat, Cek catatan, Keuangan, Alat keuangan, Kelola, dan semua form input pengelola (bagian 5.13 dan 5.14). Jangan menunggu izin saya untuk pekerjaan reversible dalam scope.
3. Di akhir setiap fase laporkan: yang berubah (path:baris), skill dan aturan yang diterapkan, output pemeriksaan bagian 7 (jumlah tes, gagal, warning lint), screenshot, dan yang belum terverifikasi.
4. Berhenti dan tanya saya hanya pada kondisi di bagian 10. Jangan push kecuali saya minta.

Larangan keras:
- Jangan mengubah backend, kontrak, migration, database, izin atau alur pekerja. Jangan login ke backend lokal saya untuk screenshot; pakai fixture MockWebServer.
- Jangan menambah warna baru, dependency, atau angka karangan. Jangan membuat animasi yang berulang terus.
- Jangan menghapus tag tes atau melemahkan assertion; pindahkan tag ke elemen pengganti. Saat dropdown diganti chip, ikuti aturan kompatibilitas di master prompt bagian 4 agar helper choose() di tes tetap lulus.
- Jangan stage file untracked atau termodifikasi milik saya (UI inspo/, assets/, .claude/, .codex/, .agents/, skills-lock.json, scripts/__pycache__/, android/.run/, docs/design/proposal-revisi-20261026/) atau secrets/env/log/build/outputs.

Mulai sekarang dari Fase 0.
```

## Jika sesi terputus di tengah

```text
Lanjutkan docs/context/MASTER_PROMPT_FRONTEND_CANOPY.md. Use antislop during for this session. Muat ulang skill sesuai bagian 2 (tanpa ponytail) dan jalankan launcher context impeccable sekali. Periksa git branch, git log dan evidence terakhir di docs/context/FRONTEND_CANOPY_EVIDENCE_*.md untuk menentukan fase yang belum selesai, laporkan posisinya, lalu lanjutkan dari sana.
```
