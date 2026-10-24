# Instruksi Agen Inofarm Jogja

## Preferensi wajib pengguna: branch per fitur — 15 Oktober 2026

Untuk pekerjaan berikutnya, buat dan pindah ke branch kerja baru yang deskriptif sebelum mengerjakan setiap fitur, perbaikan bug, atau perubahan konfigurasi/dokumentasi yang berbeda. Gunakan prefix `codex/` kecuali pengguna menentukan nama lain. Commit pada branch tersebut hanya mencakup task terkait; beberapa commit untuk fitur yang sama boleh berada di satu branch. Jangan menggabungkan fitur berbeda dalam satu branch impor/integrasi sebagai pengganti branch per fitur. Sebelum commit, pastikan branch aktif sesuai fitur, periksa diff/staging dan jalankan validasi yang relevan. Ketentuan ini tidak memberi izin commit/push otomatis; otorisasi pengguna untuk tindakan Git tetap berlaku sesuai scope.

## Otorisasi Git — 15 Oktober 2026

Pengguna memberi izin eksplisit commit per fitur dan push ke `https://student-git.umy.ac.id/andika.arya.ft23/inofarm_jogja.git` mengikuti SOP GitLab UMY. Izin ini menggantikan larangan commit/push historis untuk task publikasi tersebut; tidak memberi izin deployment atau operasi destruktif data pengguna. Riwayat remote dipertahankan, development pada branch kerja, validasi lokal dan pemeriksaan secret dilakukan sebelum push.

## Addendum implementasi 10 Oktober 2026

Pengguna telah memberi izin eksplisit Android native Kotlin, Laravel REST/MySQL, firmware ESP32, schema/kontrak, build/test/debugging dalam scope. Catatan audit lama “belum ada proyek/izin” di bawah merupakan sejarah dan digantikan otorisasi serta kondisi sekarang: modul android/backend/firmware sudah ada dan dibangun/dijalankan lokal. Ikuti docs/implementation/API.md, DECISIONS.md dan VERIFICATION.md serta update progress lima docs/context. Nilai produksi tetap kosong; hardware/calibration/RTC/SD fisik NOT_VERIFIABLE. Tidak ada izin commit/push/deploy/destructive user-data operations. Root React tetap referensi dan tidak diubah. Backend testing wajib database inofarm_test, bukan inofarm_local.

## Tujuan dan kondisi proyek

Sistem Capstone melon hidroponik NFT membantu pekerja memberi nutrisi **manual**, menerima fase/PPM handheld melalui LCD/tombol ESP32, memantau suhu air/pH/suhu ruangan/kelembapan, dan memberi owner riwayat serta validasi terhadap jadwal/parameter. Tidak ada automated dosing atau sensor TDS otomatis dalam scope proposal.

Baseline audit 10 Oktober 2026: workspace saat ini prototipe React/TypeScript dengan template Kotlin dalam `src/utils/kotlinTemplates.ts`. Belum ada Android native project, backend/database/API atau firmware pada workspace; pengguna mengonfirmasi tidak ada proyek Android terpisah. Jangan menyebut template sebagai aplikasi Kotlin terverifikasi. Native Kotlin adalah arah pengembangan yang memerlukan setup setelah task dan keputusan terkait disetujui.

Keputusan pengguna 10 Oktober 2026: database sistem menggunakan **MySQL**. Pilihan engine telah dikonfirmasi; backend Laravel/PHP disetujui; versi, environment, schema/migration, API dan deployment belum ditentukan. Jangan memilih Firebase sebagai pengganti atau menganggap database MySQL sudah dibuat/terhubung. Akses database melalui backend; credential database tidak dibundel dalam aplikasi owner. Q-11 hanya terjawab sebagian, bukan izin implementasi.

## Urutan membaca konteks

1. `AGENTS.md` ini.
2. Bagian relevan `Aturan Main/SOP GitLab UMY v2.pdf` dan `Aturan Main/SOP Pengumpulan Capstone v2.pdf` untuk pekerjaan akademik/Git/security/UI/data.
3. `docs/context/PRD.md`, terutama KF/KNF/DER terkait dan open questions.
4. `docs/context/ARCHITECTURE.md` untuk fakta current vs proposed.
5. `docs/context/DATA_CONTRACTS.md` untuk asal/field/uncertainty.
6. Task relevan dalam `docs/context/IMPLEMENTATION_PLAN.md`.
7. `docs/context/GAP_ANALYSIS.md` bila terkait temuan/coverage/compliance.
8. Source terdampak dan proposal `Aturan Main/Proposal Monitoring dan Pengingat Nutrisi Melon NFT Terbaru.docx` pada tabel/gambar yang dirujuk.

Baca bagian relevan, jangan berulang memuat seluruh dokumen tanpa kebutuhan. Proposal menentukan kebutuhan produk, SOP menentukan akademik, source menentukan implementasi aktual; rekomendasi bukan requirement disahkan. Nomor halaman proposal yang berasal daftar isi belum diverifikasi render; gunakan subbab/tabel/gambar.

## Scope dan approval

**Otorisasi lanjutan pengguna 10 Oktober 2026:** pengguna meminta prompt untuk mulai membuat Android + backend + firmware, lalu testing/debugging iteratif dalam scope tersebut. Audit-only dan permintaan approval source per task sebelumnya bukan larangan permanen setelah otorisasi ini; implementasi bertahap dalam scope telah diizinkan, tetapi turn penyusunan prompt ini hanya memperbarui dokumentasi. Keputusan terbaru: owner menetapkan fase aktif dari Android; pekerja memilih fase pada ESP32; beda fase memberi peringatan, tidak menolak/menimpa input. Semua nilai/jadwal/ambang produksi awal kosong dan diisi owner; semua reasons validasi ditampilkan. Pilihan badge utama adalah presentation, jangan menghilangkan alasan atau membuat formula agronomis. Keputusan teknik reversible seperti layout module, API contract dan test fixtures boleh dirancang/didokumentasikan sesuai lingkungan; fixture tidak menjadi produksi. Parameter belum lengkap menghasilkan belum dapat divalidasi, bukan valid. Rule agronomis yang belum ditentukan tetap tidak boleh ditebak. Commit/push/deployment, destructive actions dan perubahan di luar scope tetap memerlukan otorisasi terkait. Pertanyaan SOP/hardware yang belum terjawab hanya menahan pekerjaan yang bergantung padanya, bukan semua pekerjaan independen.

**Keputusan terbaru pengguna 10 Oktober 2026 mengatasi catatan sebelumnya:** backend **Laravel/PHP REST API + MySQL** disetujui. Validasi **B gabungan waktu/fase/PPM dan sensor** dipilih sementara untuk tahap awal; perubahan setelah testing harus dicatat/disetujui, bukan otomatis. Rancang jadwal pekerja terbaik sesuai waktu/hari konfirmasi pencatatan ESP32 (PRD §14, DER-FR-012, TASK-027): owner-managed, Asia/Jakarta, shift [start,end), lintas tengah malam, tolak overlap, assignment berdasarkan waktu sumber bukan receive time, simpan versi/snapshot historis, tanpa jadwal/clock invalid jangan menebak nama. Label **pekerja terjadwal**, bukan verified penekan tombol. Tidak menambah login pekerja atau input PPM Android. Gambar Stitch referensi UI; angka dan grafiknya contoh. Fitur input harian greenhouse tetap future scope. Threshold/rule/pinout/toolchain dan coding task belum otomatis disahkan dari pembaruan konteks ini.

Klarifikasi lanjutan pengguna 10 Oktober 2026: aplikasi hanya untuk owner/leader greenhouse; pekerja pemberi nutrisi input melalui IoT tanpa login Android. Tahap awal satu ESP32 untuk satu tandon/kolam nutrisi dan empat parameter monitoring; jumlah modul sensor fisik/model tetap mengikuti BOM yang perlu dikonfirmasi (DHT22 membaca dua parameter). Pengguna ingin jadwal pekerja per hari/shift dikelola dari aplikasi, lalu nama dikaitkan dengan waktu input perangkat; ini assignment berdasarkan jadwal, bukan identitas orang yang menekan tombol terverifikasi. Jadwal pekerja masuk rencana TASK-027 dengan rancangan PRD §14; jam/roster operasional belum diisi. Pengguna menyatakan validasi jadwal/fase/PPM serta sensor sebagai informasi/peringatan dan memengaruhi validitas catatan; pengguna telah memilih B gabungan untuk tahap awal, sisa rules/threshold masih perlu klarifikasi. Laravel/PHP REST API telah dipilih pengguna.

Keputusan scope pengguna 10 Oktober 2026: target produk **Android native Kotlin saja**; React hanya referensi tampilan APK mobile. Scope pengerjaan yang direncanakan mencakup Android, backend MySQL dan firmware ESP32 beserta API/kontrak producer-backend-consumer. Untuk tahap sekarang pencatatan PPM hanya melalui ESP32; subfitur input harian greenhouse adalah rencana masa depan yang belum memiliki requirement/kontrak/izin implementasi. Kotlin telah ditetapkan untuk Android; backend Laravel/PHP disetujui, toolchain firmware belum dikonfirmasi. Scope produk tidak membuktikan pengecualian akademik multi-platform atau hasil implementasi. Turn klarifikasi ini memperbarui keputusan/dokumentasi, bukan menjalankan coding.

Penugasan audit awal hanya dokumentasi; scope produk kini diklarifikasi pada paragraf di atas. **Tunggu persetujuan eksplisit manusia sebelum perubahan aplikasi.** Task TODO dan draft contracts tidak memberi izin implementasi. Pertanyaan PRD Q-01–Q-14 harus diselesaikan sebelum mengimplementasikan perilaku yang bergantung padanya. Target Android saja dan input PPM ESP32-only telah dipilih pengguna; penerapan SOP platform, CRUD riwayat dan sensor validation masih perlu keputusan relevan.

Jangan menciptakan threshold, jadwal, formula validasi, umur fase, cadence, timeout, endpoint atau akses akun. Angka wireframe adalah contoh; nilai kode bukan bukti persetujuan agronomis. Catat keputusan dan sumbernya, pertahankan KF-01–10 serta KNF-01–06; gunakan DER/REC untuk derivasi/rekomendasi.

## Aturan development setelah task disetujui

- Pertahankan konvensi source yang tersedia; TypeScript PascalCase komponen/type dan camelCase fungsi/field. Jangan refactor prototipe saat task hanya native.
- Untuk Kotlin baru, gunakan PascalCase class/Composable, camelCase function/property, immutable state bila sesuai, typed phase/status setelah kontrak disahkan. Package `com.inofarm.jogja` hanya kandidat dari template sampai setup disahkan. Ikuti formatter/build settings proyek native ketika sudah ada, jangan menciptakan versi SDK dari template.
- S §3 mewajibkan modular/DRY, clean code dan pemisahan presentation/business/data. UI tidak langsung memegang credential/device trust/akses database. REC-002 menawarkan Compose/ViewModel/StateFlow/repository; framework DI/domain layer/Room/WorkManager bukan kewajiban SOP.
- Pertahankan peran: hardware producer untuk sensor dan record manual; backend penyimpanan/otorisasi/validasi; dashboard owner membaca dan mengatur konfigurasi. Draft offline feedback harus membedakan provisional dan backend-authoritative.
- UI harus mengatasi loading, kosong, error, disconnected/last-known secara jujur; tidak menampilkan data/validity sukses palsu. Identitas sumber waktu/data wajib jelas. Android lifecycle/back stack diuji jika dibuat; template `remember` bukan persistence.
- Validasi form pada frontend dan backend sesuai S §1/6. Periksa struktur/finite/date/enum/rentang antar-field; aturan agronomis memakai konfigurasi disahkan. Client tidak menetapkan hak aksesnya sendiri. Password tidak plaintext. Secrets server/device tidak dikirim ke end-user atau dibundel dalam APK/web.
- API config terpusat/configurable, payload dibatasi, query/result dipaginasi; 10 KB adalah rekomendasi S §4, bukan limit kontrak otomatis. Web harus diuji subdirectory sesuai S §3 jika dipertahankan; resource root tidak disalin tanpa penilaian.
- Tambah dependency hanya dengan alasan terkait task; pin kompatibilitas setelah verifikasi dokumentasi resmi dan lingkungan. Jangan memasang package pada audit ini.
- Gunakan test unit/domain/contract/UI sesuai perubahan; uji akses, input malformed, batas parameter disahkan, waktu, offline/replay bila relevan. Mock tidak membuktikan sensor/RTC/LED/buzzer/hardware/network greenhouse. Pentest berizin wajib sebelum handover (S §1). S §7 minimal login/tambah/edit/hapus/akses, pengujian ulang otomatis direkomendasikan.
- Perbarui PRD/contract/architecture/gap/task bila perubahan terverifikasi mengubah kondisi; jangan mengubah status DONE tanpa evidence. Ketika tools/hardware tidak ada, laporkan NOT_VERIFIABLE dan alasan.

## GitLab UMY berdasarkan SOP resmi

Sumber G: `Aturan Main/SOP GitLab UMY v2.pdf` h.1–4.

- Akun kampus `@mail.umy.ac.id`, akses repository dan SSH/HTTPS token, jaringan kampus; SOP menyatakan GitLab `student-git.umy.ac.id` hanya dapat diakses jaringan UMY (§2). Jangan menyalin credential ke output.
- Clone atau init sesuai kebutuhan **hanya setelah workflow/task Git disetujui** (§4.1). Workspace audit tidak punya metadata `.git`; jangan mengasumsikan branch/remote.
- Semua perubahan development pada branch kerja, bukan main/master; branch baru untuk fitur/bug/config. Nama deskriptif dianjurkan, bukan wajib pola GitFlow (§3,4.2,6).
- Validasi lokal sebelum commit/push. Periksa `git status`/`git diff`; stage file relevan, hindari env/credential/log/artifact/temp (§4.3–4.5).
- Pesan commit ringkas, spesifik/deskriptif/konsisten. `type(scope): message` **boleh**, bukan mandatory (§4.6).
- Sinkronkan branch dengan remote sebelum push; selesaikan konflik pada branch kerja dan ulangi validasi. Push setelah commit dan validasi lulus, kemudian verifikasi commit/branch di remote (§4.7–4.8,8).
- MR **jika workflow mewajibkan review**; deskripsi ringkasan/alasan/dampak teknis (§4.9). Jika CI dipakai, pastikan pipeline berjalan setelah push (§8). SOP tidak menetapkan quota commit, reviewer count, CI wajib, atau struktur folder khusus.
- Pengguna mewajibkan otorisasi sebelum commit/push; jangan mengartikan SOP sebagai izin commit/push saat ini. Jangan destructive Git/reset/rebase history yang berisiko tanpa otorisasi.

Sumber S: pentest/auth/form/input/file limits bila upload ada; UI multi-platform/feedback; layer/API/env/request/query/full CRUD; login/tambah/edit/hapus/akses test; submit dokumentasi ke platform yang ditentukan, reviewer periksa dan revisi bila perlu. Lihat compliance matrix GAP_ANALYSIS untuk kewajiban vs recommendation dan konflik. Jangan mengklaim native Android memenuhi Windows/macOS/iOS atau menerapkan edit/delete historis tanpa keputusan manusia.

## Agent execution protocol

1. Identifikasi task yang disetujui, requirement dan boundaries.
2. Baca section sumber dan keputusan Q yang diperlukan; bila belum diputuskan, progress hanya bagian independen dan minta klarifikasi.
3. Inspeksi source aktual, status kerja dan instruksi lokal; jangan menimpa perubahan orang lain.
4. Jelaskan intended changes dan hasil yang akan diverifikasi.
5. Implementasikan hanya scope assigned setelah izin berlaku.
6. Hindari unrelated refactor, sensor/fitur/dependency tambahan tanpa kebutuhan.
7. Jalankan check/test sesuai stack yang benar-benar tersedia; jangan menjalankan `clean` yang menghapus file tanpa kebutuhan/izin.
8. Verifikasi AC terhadap hasil dan catat limitations.
9. Laporkan files changed dan alasan.
10. Laporkan unresolved problems dan konflik requirement.
11. Update progress setelah verification, bukan setelah UI tampak selesai.

## Larangan

Jangan invent business rules, automated dosing, automatic TDS, API/deployment fictitious; jangan add dependency yang tidak diperlukan; jangan hapus working functionality tanpa alasan disetujui; jangan expose credential; jangan destructive Git tanpa persetujuan; jangan commit/push tanpa otorisasi; jangan menyatakan untested work verified; jangan memindahkan proposal/SOP/source sekadar agar struktur cocok diagram dokumentasi.

## Definition of Done

Requirement/AC terkait terpenuhi; applicable checks/test lulus; existing functionality tetap diperiksa; dokumentasi diperbarui jika perlu; perubahan direview dengan evidence; hasil, file dan keterbatasan dilaporkan secara akurat. Hardware/remote/reviewer checks yang belum ada tetap terbuka, bukan DONE. Pentest dan review final merupakan gate handover, bukan klaim dari audit source.
