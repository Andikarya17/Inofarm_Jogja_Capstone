# Register sumber dan batas audit

Tanggal audit 29 Oktober 2026, zona Asia/Jakarta. Source baseline HEAD `9da5b30bfe707f94b64028de98878c2e55adccea`; branch awal `codex/proposal-unified-flowchart`, branch task `codex/finance-proposal-context`.

## Sumber primer

P: `D:/Semester 7/Capstone 2/Proposal/Proposal Aplikasi Pencatatan Keuangan .pdf`, 60 halaman PDF, SHA256 `e4d7389e2fb1385d43ed507f33d211b7e66f92eae1bade4f15c64c6cf3bfc273`. Judul sampul: Aplikasi Mobile Pencatatan Operasional dan Evaluasi Keuangan Greenhouse Melon Hidroponik. Tidak menyalin PDF ke repo tanpa kebutuhan.

Nomor berikut adalah urutan **halaman PDF 1-based**, bukan nomor cetak/daftar isi yang tidak konsisten.

| Bagian | Halaman PDF | Isi yang menjadi dasar |
|---|---|---|
| §1.1–1.5 | 9–13 | Latar belakang, dua peran, scope tanpa IoT, batas keuangan |
| Tabel 2.3 | 16–17 | KF-01 sampai KF-14 |
| Tabel 2.4 | 17–18 | KNF-01 sampai KNF-08 |
| §2.2.1–8 | 18–38 | Arsitektur, role, aktivitas, sync, model, ERD, flow |
| §2.2.9; Tabel 2.5–2.6 | 39–42 | Form, empat dimensi status, HSS/HST, target |
| §2.2.10; Tabel 2.7–2.9 | 42–47 | Transaksi, alokasi, penyusutan, rumus dan fixture |
| Tabel 2.10 | 47–48 | Layar utama |
| §2.2.12 | 48–50 | Security, ekspor snapshot, backup/restore |
| BAB III | 51–57 | Tahapan, 16 minggu, alat, U-01–U-21, UAT/SOP |

Teks seluruh PDF diekstrak. Halaman 16–47 dirender; arsitektur, use case, activity, sequence, class, ERD, user flow dan flowchart diperiksa melalui montage; ERD halaman 30–31 juga diperiksa pada gambar halaman penuh. Parser memberi warning font Symbol/ArialUnicode; teks dan gambar saling diperiksa, bukan klaim proofreading visual 60 halaman. Detail tabel rumus dibaca dari ekstraksi. Temp ada di `tmp/finance-audit/` (diabaikan Git).

G: `Aturan Main/SOP GitLab UMY v2.pdf`, 4 halaman: branch per fitur, validasi, secret, sync remote, publikasi dan review kondisional.
S: `Aturan Main/SOP Pengumpulan Capstone v2.pdf`, 3 halaman: auth/pentest, form, UI, arsitektur, API, CRUD, test dan handover.
U: instruksi pengguna 29 Oktober 2026 memilih proposal P serta Kotlin/Compose + Laravel + MySQL dan meminta audit, master prompt, perubahan context pack.

## Metode pemeriksaan repo

Inventaris rekursif file tracked dan untracked non-ignored: [SOURCE_INVENTORY.json](SOURCE_INVENTORY.json), 344 file pada saat scan (sebelum dokumen baru). Manifest mencatat path, ukuran, SHA256; bukan bukti seluruh baris direview manual. Inspeksi rinci terfokus pada entrypoint, route, migration, auth, model, cache/repository/ViewModel/navigation, firmware configuration/core contract, scripts dan test; pencarian simbol lintas folder menutup pemetaan modul. Dependency vendor/node_modules, runtime, environment rahasia, build/cache dan database nyata tidak dimasukkan audit isi. Source menentukan implementasi; diagram bukan bukti fitur sudah ada.

Tidak menjalankan aplikasi, build, unit/instrumentation test, migration atau SQL dalam task dokumentasi ini. Evidence PASS lama tetap historis di `docs/implementation/VERIFICATION.md`. Tidak ada klaim runtime baru atau pentest.

## Perubahan lokal awal

Lima `docs/context/*.md` sudah modified; `docs/design/proposal-revisi-20261026/` untracked. Delapan file termasuk lima context tersebut disalin byte-identik ke `docs/archive/pre-finance-20261029/`, SHA256 di manifest. Folder desain tidak diubah. Tidak reset, stash, hapus data atau stage perubahan lama.
