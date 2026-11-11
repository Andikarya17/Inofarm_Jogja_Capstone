# Inventaris database terverifikasi, 10 November 2026

Baseline setelah rollout19 dan remediasi10 November2026. Struktur target diambil dari `inofarm_test` setelah suite guarded menjalankan migration 1–19; database aplikasi `inofarm_finance_local` kini sampai migration19. Lihat AUDIT_REMEDIATION_20261110.md untuk backup dan rekonsiliasi. Tipe kolom/FK di bawah berasal dari information_schema MySQL 8.0.30. Ini bukan dump isi data atau instruksi migrate.

Ringkasan: lokal 54 tabel/75 FK/24 migration total; uji 54 tabel/75 FK/24 migration total. Lima migration awal mencakup infrastruktur dan legacy, sehingga angka total berbeda dari nomor migration finance. Tidak ditemukan orphan pada FK yang diperiksa. Tidak ada perbedaan tipe/nullability/default/key untuk kolom yang ada di kedua database.

Tabel legacy dipertahankan sesuai keputusan pengguna, bukan API IoT aktif. Tabel teknis mendukung Laravel/sesi. Tabel domain dan ledger mendukung operasional/keuangan. FK bukan bukti seluruh aturan saldo/scope: lihat audit dan services.

| Tabel | Kategori | Baris lokal | Kolom lokal / target |
|---|---|---:|---:|
| `activities` | domain/ledger | 1 | 19 / 19 |
| `activity_materials` | domain/ledger | 0 | 5 / 5 |
| `activity_reference_snapshots` | domain/ledger | 1 | 4 / 4 |
| `activity_reviews` | domain/ledger | 0 | 8 / 8 |
| `assets` | domain/ledger | 0 | 15 / 15 |
| `audit_logs` | domain/ledger | 12 | 10 / 10 |
| `backup_runs` | domain/ledger | 0 | 8 / 8 |
| `cache` | infrastruktur | 0 | 3 / 3 |
| `cache_locks` | infrastruktur | 0 | 3 / 3 |
| `change_feed` | domain/ledger | 16 | 9 / 9 |
| `config_versions` | legacy | 0 | 5 / 5 |
| `cost_allocations` | domain/ledger | 0 | 14 / 14 |
| `cultivation_reference_versions` | domain/ledger | 0 | 5 / 5 |
| `depreciation_allocations` | domain/ledger | 0 | 18 / 18 |
| `devices` | legacy | 0 | 9 / 9 |
| `entity_identities` | domain/ledger | 14 | 3 / 3 |
| `events` | legacy | 0 | 16 / 16 |
| `expense_categories` | domain/ledger | 0 | 7 / 7 |
| `expenses` | domain/ledger | 0 | 18 / 18 |
| `failed_jobs` | infrastruktur | 0 | 7 / 7 |
| `grades` | domain/ledger | 2 | 7 / 7 |
| `greenhouse_assignments` | domain/ledger | 2 | 10 / 10 |
| `greenhouses` | domain/ledger | 1 | 9 / 9 |
| `harvest_items` | domain/ledger | 2 | 11 / 11 |
| `harvest_reference_snapshots` | domain/ledger | 2 | 4 / 4 |
| `harvests` | domain/ledger | 1 | 9 / 9 |
| `investment_cash_flow_versions` | domain/ledger | 0 | 7 / 7 |
| `investment_scenarios` | domain/ledger | 0 | 15 / 15 |
| `job_batches` | infrastruktur | 0 | 10 / 10 |
| `jobs` | infrastruktur | 0 | 7 / 7 |
| `materials` | domain/ledger | 0 | 8 / 8 |
| `migrations` | infrastruktur | 24 | 3 / 3 |
| `password_reset_tokens` | infrastruktur | 0 | 3 / 3 |
| `personal_access_tokens` | infrastruktur | 2 | 10 / 10 |
| `phase_versions` | legacy | 0 | 5 / 5 |
| `planting_groups` | domain/ledger | 1 | 13 / 13 |
| `record_attachments` | domain/ledger | 0 | 15 / 15 |
| `report_snapshots` | domain/ledger | 0 | 12 / 12 |
| `restore_runs` | domain/ledger | 0 | 11 / 11 |
| `sale_items` | domain/ledger | 0 | 11 / 11 |
| `sales` | domain/ledger | 0 | 20 / 20 |
| `schedules` | domain/ledger | 0 | 9 / 9 |
| `seasons` | domain/ledger | 1 | 9 / 9 |
| `sessions` | infrastruktur | 0 | 6 / 6 |
| `shift_versions` | legacy | 0 | 8 / 8 |
| `sync_operations` | domain/ledger | 12 | 9 / 9 |
| `sync_state` | domain/ledger | 1 | 2 / 2 |
| `tanks` | legacy | 0 | 4 / 4 |
| `targets` | domain/ledger | 0 | 15 / 15 |
| `units` | domain/ledger | 0 | 8 / 8 |
| `users` | domain/ledger | 2 | 13 / 13 |
| `varieties` | domain/ledger | 1 | 7 / 7 |
| `worker_versions` | legacy | 0 | 5 / 5 |
| `workers` | legacy | 0 | 4 / 4 |

## Kamus fisik target dan selisih lokal

PK/UNI/MUL mengikuti information_schema. Kolom tanpa FK dapat berupa snapshot/relasi polimorfik yang divalidasi service, bukan otomatis kekurangan FK. Nilai default SQL NULL ditulis `NULL`; status nullable terpisah. Jumlah baris tidak membuktikan kelengkapan transaksi usaha.

### activities

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `planting_group_id` | `char(36)` | NO | `NULL` | MUL | planting_groups.id | ada |
| `schedule_id` | `char(36)` | YES | `NULL` | MUL | schedules.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `activity_type` | `varchar(30)` | NO | `NULL` |  |  | ada |
| `occurred_at` | `datetime(6)` | NO | `NULL` |  |  | ada |
| `manual_ppm` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `manual_ph` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `actual_stage` | `varchar(100)` | YES | `NULL` |  |  | ada |
| `work_state` | `varchar(20)` | NO | `NULL` |  |  | ada |
| `skipped_reason` | `text` | YES | `NULL` |  |  | ada |
| `notes` | `text` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `manual_ec` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `recorded_by_role` | `varchar(20)` | YES | `NULL` | MUL |  | ada |
| `observations` | `json` | YES | `NULL` |  |  | ada |

### activity_materials

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `activity_id` | `char(36)` | NO | `NULL` | MUL | activities.id | ada |
| `material_id` | `char(36)` | NO | `NULL` | MUL | materials.id | ada |
| `unit_id` | `char(36)` | NO | `NULL` | MUL | units.id | ada |
| `quantity` | `decimal(20,6)` | NO | `NULL` |  |  | ada |

### activity_reference_snapshots

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `activity_id` | `char(36)` | NO | `NULL` | MUL | activities.id | ada |
| `activity_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |

### activity_reviews

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `activity_id` | `char(36)` | NO | `NULL` | MUL | activities.id | ada |
| `activity_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `reviewer_id` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `reviewer_name` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `decision` | `varchar(12)` | NO | `NULL` |  |  | ada |
| `reason` | `text` | YES | `NULL` |  |  | ada |
| `reviewed_at` | `datetime(6)` | NO | `NULL` |  |  | ada |

### assets

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `expense_id` | `char(36)` | NO | `NULL` | MUL | expenses.id | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `cost` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `residual` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `life_years` | `int unsigned` | NO | `NULL` |  |  | ada |
| `in_service_date` | `date` | NO | `NULL` |  |  | ada |
| `usage_basis` | `text` | NO | `NULL` |  |  | ada |
| `formula_version` | `varchar(60)` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### audit_logs

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `actor_id` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `operation_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `entity_type` | `varchar(40)` | NO | `NULL` | MUL |  | ada |
| `entity_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `action` | `varchar(20)` | NO | `NULL` |  |  | ada |
| `version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `reason` | `text` | YES | `NULL` |  |  | ada |
| `changes` | `json` | NO | `NULL` |  |  | ada |
| `server_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |

### backup_runs

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `actor_id` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `reason` | `text` | NO | `NULL` |  |  | ada |
| `state` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `manifest_hash` | `char(64)` | YES | `NULL` |  |  | ada |
| `summary` | `json` | YES | `NULL` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### cache

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `key` | `varchar(255)` | NO | `NULL` | PRI |  | ada |
| `value` | `mediumtext` | NO | `NULL` |  |  | ada |
| `expiration` | `bigint` | NO | `NULL` | MUL |  | ada |

### cache_locks

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `key` | `varchar(255)` | NO | `NULL` | PRI |  | ada |
| `owner` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `expiration` | `bigint` | NO | `NULL` | MUL |  | ada |

### change_feed

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `sequence` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `audience` | `varchar(20)` | NO | `all` |  |  | ada |
| `entity_type` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `entity_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `tombstone` | `tinyint(1)` | NO | `0` |  |  | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |
| `committed_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |

### config_versions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `tank_id` | `bigint unsigned` | NO | `NULL` | MUL | tanks.id | ada |
| `created_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |
| `effective_from` | `timestamp(6)` | NO | `NULL` |  |  | ada |

### cost_allocations

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `expense_id` | `char(36)` | NO | `NULL` | MUL | expenses.id | ada |
| `season_id` | `char(36)` | NO | `NULL` | MUL | seasons.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `amount` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `currency` | `varchar(3)` | NO | `IDR` |  |  | ada |
| `cost_class` | `varchar(2)` | NO | `NULL` |  |  | ada |
| `allocation_basis` | `text` | NO | `NULL` |  |  | ada |
| `season_name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `season_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### cultivation_reference_versions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `entity_type` | `varchar(20)` | NO | `NULL` | MUL |  | ada |
| `entity_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |

### depreciation_allocations

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `asset_id` | `char(36)` | NO | `NULL` | MUL | assets.id | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `season_id` | `char(36)` | NO | `NULL` | MUL | seasons.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `asset_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `usage_year` | `int unsigned` | NO | `NULL` |  |  | ada |
| `period_start` | `date` | NO | `NULL` |  |  | ada |
| `period_end` | `date` | NO | `NULL` |  |  | ada |
| `usage_reference` | `char(36)` | NO | `NULL` |  |  | ada |
| `amount` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `year_budget` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `allocation_basis` | `text` | NO | `NULL` |  |  | ada |
| `formula_version` | `varchar(60)` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### devices

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `tank_id` | `bigint unsigned` | NO | `NULL` | MUL | tanks.id | ada |
| `name` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `token_hash` | `varchar(64)` | NO | `NULL` | UNI |  | ada |
| `enabled` | `tinyint(1)` | NO | `1` |  |  | ada |
| `last_contact_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `applied_config_version` | `bigint unsigned` | YES | `NULL` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### entity_identities

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `entity_id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `entity_type` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `reserved_at` | `timestamp(6)` | NO | `CURRENT_TIMESTAMP(6)` |  |  | ada |

### events

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `event_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `device_id` | `char(36)` | NO | `NULL` | MUL | devices.id | ada |
| `tank_id` | `bigint unsigned` | NO | `NULL` | MUL | tanks.id | ada |
| `kind` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `payload_hash` | `varchar(64)` | NO | `NULL` |  |  | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |
| `source_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `received_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `phase` | `varchar(255)` | YES | `NULL` |  |  | ada |
| `manual_ppm` | `decimal(12,3)` | YES | `NULL` |  |  | ada |
| `validation_result` | `json` | YES | `NULL` |  |  | ada |
| `assignment` | `json` | YES | `NULL` |  |  | ada |
| `config_version_id` | `bigint unsigned` | YES | `NULL` | MUL | config_versions.id | ada |
| `phase_version_id` | `bigint unsigned` | YES | `NULL` | MUL | phase_versions.id | ada |
| `clock_trusted` | `tinyint(1)` | NO | `0` |  |  | ada |

### expense_categories

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### expenses

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `balance_id` | `char(36)` | NO | `NULL` | UNI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `category_id` | `char(36)` | NO | `NULL` | MUL | expense_categories.id | ada |
| `category_name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `category_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `paid_at` | `datetime(6)` | NO | `NULL` |  |  | ada |
| `currency` | `varchar(3)` | NO | `IDR` |  |  | ada |
| `amount` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `payment_kind` | `varchar(24)` | NO | `NULL` |  |  | ada |
| `notes` | `text` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `allocated_amount` | `decimal(20,2)` | NO | `0.00` |  |  | ada |
| `balance_version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### failed_jobs

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `uuid` | `varchar(255)` | NO | `NULL` | UNI |  | ada |
| `connection` | `varchar(255)` | NO | `NULL` | MUL |  | ada |
| `queue` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `payload` | `longtext` | NO | `NULL` |  |  | ada |
| `exception` | `longtext` | NO | `NULL` |  |  | ada |
| `failed_at` | `timestamp` | NO | `CURRENT_TIMESTAMP` |  |  | ada |

### grades

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### greenhouse_assignments

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `user_id` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `effective_from` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `effective_until` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### greenhouses

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `running_group_id` | `char(36)` | YES | `NULL` |  |  | ada |
| `running_period_reviewed` | `tinyint(1)` | NO | `0` |  |  | ada |

### harvest_items

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `harvest_id` | `char(36)` | NO | `NULL` | MUL | harvests.id | ada |
| `grade_id` | `char(36)` | NO | `NULL` | MUL | grades.id | ada |
| `grade_name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `grade_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `fruit_count` | `int unsigned` | NO | `NULL` |  |  | ada |
| `weight_kg` | `decimal(20,6)` | NO | `NULL` |  |  | ada |
| `sold_weight_kg` | `decimal(20,6)` | NO | `0.000000` |  |  | ada |
| `balance_version` | `int unsigned` | NO | `1` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `quality` | `varchar(12)` | YES | `NULL` |  |  | ada |

### harvest_reference_snapshots

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `harvest_id` | `char(36)` | NO | `NULL` | MUL | harvests.id | ada |
| `harvest_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |

### harvests

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `planting_group_id` | `char(36)` | NO | `NULL` | MUL | planting_groups.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `occurred_at` | `datetime(6)` | NO | `NULL` |  |  | ada |
| `notes` | `text` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### investment_cash_flow_versions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `scenario_id` | `char(36)` | NO | `NULL` | PRI | investment_scenarios.id | ada |
| `scenario_version` | `int unsigned` | NO | `NULL` | PRI |  | ada |
| `year` | `smallint unsigned` | NO | `NULL` | PRI |  | ada |
| `amount` | `decimal(20,2)` | YES | `NULL` |  |  | ada |
| `source_kind` | `varchar(16)` | YES | `NULL` |  |  | ada |
| `source_reference` | `varchar(200)` | NO | `NULL` |  |  | ada |
| `basis` | `text` | NO | `NULL` |  |  | ada |

### investment_scenarios

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(16)` | NO | `active` |  |  | ada |
| `name` | `varchar(120)` | NO | `NULL` |  |  | ada |
| `base_date` | `date` | NO | `NULL` |  |  | ada |
| `horizon_years` | `smallint unsigned` | NO | `NULL` |  |  | ada |
| `initial_outlay` | `decimal(20,2)` | YES | `NULL` |  |  | ada |
| `initial_source` | `json` | NO | `NULL` |  |  | ada |
| `discount_rate` | `decimal(20,8)` | YES | `NULL` |  |  | ada |
| `payback_convention` | `varchar(24)` | YES | `NULL` |  |  | ada |
| `notes` | `text` | NO | `NULL` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### job_batches

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `varchar(255)` | NO | `NULL` | PRI |  | ada |
| `name` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `total_jobs` | `int` | NO | `NULL` |  |  | ada |
| `pending_jobs` | `int` | NO | `NULL` |  |  | ada |
| `failed_jobs` | `int` | 0 | 7 / 7 |  |  | ada |
| `failed_job_ids` | `longtext` | NO | `NULL` |  |  | ada |
| `options` | `mediumtext` | YES | `NULL` |  |  | ada |
| `cancelled_at` | `int` | YES | `NULL` |  |  | ada |
| `created_at` | `int` | NO | `NULL` |  |  | ada |
| `finished_at` | `int` | YES | `NULL` |  |  | ada |

### jobs

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `queue` | `varchar(255)` | NO | `NULL` | MUL |  | ada |
| `payload` | `longtext` | NO | `NULL` |  |  | ada |
| `attempts` | `smallint unsigned` | NO | `NULL` |  |  | ada |
| `reserved_at` | `int unsigned` | YES | `NULL` |  |  | ada |
| `available_at` | `int unsigned` | NO | `NULL` |  |  | ada |
| `created_at` | `int unsigned` | NO | `NULL` |  |  | ada |

### materials

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `dimension` | `varchar(30)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### migrations

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `int unsigned` | NO | `NULL` | PRI |  | ada |
| `migration` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `batch` | `int` | NO | `NULL` |  |  | ada |

### password_reset_tokens

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `email` | `varchar(255)` | NO | `NULL` | PRI |  | ada |
| `token` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `created_at` | `timestamp` | YES | `NULL` |  |  | ada |

### personal_access_tokens

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `tokenable_type` | `varchar(255)` | NO | `NULL` | MUL |  | ada |
| `tokenable_id` | `bigint unsigned` | NO | `NULL` |  |  | ada |
| `name` | `text` | NO | `NULL` |  |  | ada |
| `token` | `varchar(64)` | NO | `NULL` | UNI |  | ada |
| `abilities` | `text` | YES | `NULL` |  |  | ada |
| `last_used_at` | `timestamp` | YES | `NULL` |  |  | ada |
| `expires_at` | `timestamp` | YES | `NULL` | MUL |  | ada |
| `created_at` | `timestamp` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp` | YES | `NULL` |  |  | ada |

### phase_versions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `tank_id` | `bigint unsigned` | NO | `NULL` | MUL | tanks.id | ada |
| `created_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |
| `effective_from` | `timestamp(6)` | NO | `NULL` |  |  | ada |

### planting_groups

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `season_id` | `char(36)` | NO | `NULL` | MUL | seasons.id | ada |
| `variety_id` | `char(36)` | NO | `NULL` | MUL | varieties.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `population` | `int unsigned` | NO | `NULL` |  |  | ada |
| `sowing_date` | `date` | NO | `NULL` |  |  | ada |
| `transplant_date` | `date` | YES | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `harvest_target` | `json` | YES | `NULL` |  |  | ada |
| `harvest_completion` | `json` | YES | `NULL` |  |  | ada |

### record_attachments

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `owner_type` | `varchar(20)` | NO | `NULL` | MUL |  | ada |
| `owner_id` | `char(36)` | NO | `NULL` | MUL | entity_identities.entity_id | ada |
| `owner_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `recorded_by_name` | `varchar(100)` | NO | `NULL` |  |  | ada |
| `original_name` | `varchar(200)` | NO | `NULL` |  |  | ada |
| `mime` | `varchar(80)` | NO | `NULL` |  |  | ada |
| `bytes` | `int unsigned` | NO | `NULL` |  |  | ada |
| `sha256` | `varchar(64)` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### report_snapshots

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `kind` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `season_id` | `char(36)` | YES | `NULL` | MUL | seasons.id | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `snapshot_hash` | `varchar(64)` | NO | `NULL` |  |  | ada |
| `sources_hash` | `varchar(64)` | NO | `NULL` |  |  | ada |
| `source_count` | `int unsigned` | NO | `NULL` |  |  | ada |
| `content` | `json` | NO | `NULL` |  |  | ada |
| `created_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `scenario_id` | `char(36)` | YES | `NULL` | MUL | investment_scenarios.id | ada |

### restore_runs

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `backup_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `operator_reference` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `reason` | `text` | NO | `NULL` |  |  | ada |
| `state` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `manifest_hash` | `char(64)` | NO | `NULL` |  |  | ada |
| `old_epoch` | `char(36)` | NO | `NULL` |  |  | ada |
| `new_epoch` | `char(36)` | YES | `NULL` |  |  | ada |
| `verification` | `json` | NO | `NULL` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### sale_items

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `sale_id` | `char(36)` | NO | `NULL` | MUL | sales.id | ada |
| `harvest_item_id` | `char(36)` | NO | `NULL` | MUL | harvest_items.id | ada |
| `harvest_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `harvest_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `grade_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `grade_name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `quantity_kg` | `decimal(20,6)` | NO | `NULL` |  |  | ada |
| `price_per_kg` | `decimal(20,6)` | NO | `NULL` |  |  | ada |
| `exact_amount` | `decimal(40,12)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |

### sales

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `season_id` | `char(36)` | NO | `NULL` | MUL | seasons.id | ada |
| `season_name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `season_version` | `int unsigned` | NO | `NULL` |  |  | ada |
| `recorded_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `recorded_by_name` | `varchar(100)` | NO | `NULL` |  |  | ada |
| `paid_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `payment_status` | `varchar(20)` | NO | `NULL` |  |  | ada |
| `customer_name` | `varchar(200)` | NO | `NULL` |  |  | ada |
| `currency` | `varchar(3)` | NO | `NULL` |  |  | ada |
| `total_amount` | `decimal(20,2)` | NO | `NULL` |  |  | ada |
| `total_weight_kg` | `decimal(20,6)` | NO | `NULL` |  |  | ada |
| `exact_total` | `decimal(40,12)` | NO | `NULL` |  |  | ada |
| `rounding` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `notes` | `text` | NO | `NULL` |  |  | ada |
| `record_state` | `varchar(20)` | NO | `active` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### schedules

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `planting_group_id` | `char(36)` | NO | `NULL` | MUL | planting_groups.id | ada |
| `activity_type` | `varchar(30)` | NO | `NULL` |  |  | ada |
| `planned_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `notes` | `text` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### seasons

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `start_date` | `date` | NO | `NULL` |  |  | ada |
| `end_date` | `date` | YES | `NULL` |  |  | ada |
| `status` | `varchar(20)` | NO | `open` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### sessions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `varchar(255)` | NO | `NULL` | PRI |  | ada |
| `user_id` | `bigint unsigned` | YES | `NULL` | MUL |  | ada |
| `ip_address` | `varchar(45)` | YES | `NULL` |  |  | ada |
| `user_agent` | `text` | YES | `NULL` |  |  | ada |
| `payload` | `longtext` | NO | `NULL` |  |  | ada |
| `last_activity` | `int` | NO | `NULL` | MUL |  | ada |

### shift_versions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `shift_key` | `char(36)` | NO | `NULL` | MUL |  | ada |
| `tank_id` | `bigint unsigned` | NO | `NULL` | MUL | tanks.id | ada |
| `worker_id` | `bigint unsigned` | NO | `NULL` | MUL | workers.id | ada |
| `body` | `json` | NO | `NULL` |  |  | ada |
| `effective_from` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `effective_until` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `created_by` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |

### sync_operations

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `actor_id` | `bigint unsigned` | NO | `NULL` | MUL | users.id | ada |
| `operation_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `entity_id` | `char(36)` | NO | `NULL` |  |  | ada |
| `entity_type` | `varchar(40)` | NO | `NULL` |  |  | ada |
| `request_hash` | `varchar(64)` | NO | `NULL` |  |  | ada |
| `receipt` | `json` | NO | `NULL` |  |  | ada |
| `http_status` | `smallint unsigned` | NO | `NULL` |  |  | ada |
| `committed_at` | `timestamp(6)` | NO | `NULL` |  |  | ada |

### sync_state

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `tinyint unsigned` | NO | `NULL` | PRI |  | ada |
| `epoch` | `char(36)` | NO | `NULL` |  |  | ada |

### tanks

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `name` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### targets

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `planting_group_id` | `char(36)` | NO | `NULL` | MUL | planting_groups.id | ada |
| `activity_type` | `varchar(30)` | NO | `NULL` |  |  | ada |
| `parameter` | `varchar(30)` | NO | `NULL` |  |  | ada |
| `unit` | `varchar(10)` | NO | `NULL` |  |  | ada |
| `target_value` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `minimum` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `maximum` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `tolerance` | `decimal(20,6)` | YES | `NULL` |  |  | ada |
| `effective_from` | `timestamp(6)` | NO | `NULL` |  |  | ada |
| `effective_until` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### units

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `dimension` | `varchar(30)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### users

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `name` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `email` | `varchar(255)` | NO | `NULL` | UNI |  | ada |
| `email_verified_at` | `timestamp` | YES | `NULL` |  |  | ada |
| `password` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `remember_token` | `varchar(100)` | YES | `NULL` |  |  | ada |
| `created_at` | `timestamp` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp` | YES | `NULL` |  |  | ada |
| `tank_id` | `bigint unsigned` | YES | `NULL` | MUL | tanks.id | ada |
| `role` | `varchar(255)` | NO | `owner` |  |  | ada |
| `public_id` | `char(36)` | YES | `NULL` | UNI |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |

### varieties

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `char(36)` | NO | `NULL` | PRI |  | ada |
| `greenhouse_id` | `char(36)` | NO | `NULL` | MUL | greenhouses.id | ada |
| `name` | `varchar(150)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `1` |  |  | ada |
| `version` | `int unsigned` | NO | `1` |  |  | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |

### worker_versions

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `worker_id` | `bigint unsigned` | NO | `NULL` | MUL | workers.id | ada |
| `name` | `varchar(255)` | NO | `NULL` |  |  | ada |
| `active` | `tinyint(1)` | NO | `NULL` |  |  | ada |
| `effective_from` | `timestamp(6)` | NO | `NULL` |  |  | ada |

### workers

| Kolom | Tipe | Nullable | Default | Key | FK ke | Lokal |
|---|---|---|---|---|---|---|
| `id` | `bigint unsigned` | NO | `NULL` | PRI |  | ada |
| `tank_id` | `bigint unsigned` | NO | `NULL` | MUL | tanks.id | ada |
| `created_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
| `updated_at` | `timestamp(6)` | YES | `NULL` |  |  | ada |
