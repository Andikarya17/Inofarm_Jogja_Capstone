#!/usr/bin/env bash
# Backup mysqldump bertanggal dengan checksum, untuk server Linux (cron). Kredensial dari env DB_HOST, DB_PORT,
# DB_DATABASE, DB_USERNAME, DB_PASSWORD; kunci yang belum ada di env diisi dari berkas env pada argumen kedua.
# Password tidak pernah dicetak atau ditulis ke argumen proses. Simpan direktori keluaran di luar repo dan webroot.
#   scripts/backup-mysql.sh /var/backups/inofarm /var/www/inofarm/backend/.env
set -euo pipefail
out_dir=${1:?"pakai: backup-mysql.sh <direktori-keluaran> [berkas-env]"}
env_file=${2:-}
retention_days=${RETENTION_DAYS:-14}
mysqldump_bin=${MYSQLDUMP:-mysqldump}
for key in DB_HOST DB_PORT DB_DATABASE DB_USERNAME DB_PASSWORD; do
    if [ -z "${!key:-}" ] && [ -n "$env_file" ]; then
        value=$(sed -n "s/^[[:space:]]*$key[[:space:]]*=[[:space:]]*//p" "$env_file" | tail -n 1 | tr -d '\r')
        value=${value%\"}; value=${value#\"}; value=${value%\'}; value=${value#\'}
        printf -v "$key" '%s' "$value"
    fi
done
for key in DB_HOST DB_PORT DB_DATABASE DB_USERNAME; do
    [ -n "${!key:-}" ] || { echo "$key belum diisi" >&2; exit 2; }
done
umask 077
mkdir -p "$out_dir"
file="$out_dir/$DB_DATABASE-$(date +%Y%m%d-%H%M%S).sql"
MYSQL_PWD="${DB_PASSWORD:-}" "$mysqldump_bin" --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USERNAME" --single-transaction \
    --routines --triggers --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 --result-file="$file" "$DB_DATABASE"
# Dump yang terpotong tidak punya penanda ini; jangan dihitung sebagai cadangan.
grep -q '^-- Dump completed' "$file" || { rm -f "$file"; echo 'Dump tidak lengkap' >&2; exit 1; }
(cd "$out_dir" && sha256sum "$(basename "$file")" > "$(basename "$file").sha256")
find "$out_dir" -maxdepth 1 -type f -name "$DB_DATABASE-*.sql*" -mtime +"$retention_days" -delete
echo "$file"
cut -d' ' -f1 "$file.sha256"
