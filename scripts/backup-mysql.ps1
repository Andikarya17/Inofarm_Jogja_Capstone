# Backup mysqldump bertanggal dengan checksum. Kredensial dari env DB_HOST, DB_PORT, DB_DATABASE, DB_USERNAME, DB_PASSWORD;
# kunci yang belum ada di env diisi dari -EnvFile. Password tidak pernah dicetak atau ditulis ke argumen proses.
# Simpan -OutDir di luar repo dan di luar webroot. Contoh:
#   ./scripts/backup-mysql.ps1 -OutDir D:/inofarm-backup -EnvFile backend/.env
param([Parameter(Mandatory)][string]$OutDir, [string]$EnvFile = '', [int]$RetentionDays = 14,
    [string]$MysqlDump = 'C:/laragon/bin/mysql/mysql-8.0.30-winx64/bin/mysqldump.exe')
$ErrorActionPreference = 'Stop'
$keys = 'DB_HOST', 'DB_PORT', 'DB_DATABASE', 'DB_USERNAME', 'DB_PASSWORD'
$db = @{}
foreach ($key in $keys) { $db[$key] = [Environment]::GetEnvironmentVariable($key) }
if ($EnvFile) {
    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match '^\s*(DB_[A-Z]+)\s*=\s*(.*?)\s*$' -and $keys -contains $Matches[1] -and -not $db[$Matches[1]]) {
            $db[$Matches[1]] = $Matches[2] -replace '^"(.*)"$', '$1' -replace "^'(.*)'$", '$1'
        }
    }
}
foreach ($key in 'DB_HOST', 'DB_PORT', 'DB_DATABASE', 'DB_USERNAME') { if (-not $db[$key]) { throw "$key belum diisi" } }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$file = Join-Path $OutDir ("{0}-{1}.sql" -f $db.DB_DATABASE, (Get-Date -Format 'yyyyMMdd-HHmmss'))
$previous = $env:MYSQL_PWD
$env:MYSQL_PWD = $db.DB_PASSWORD
try {
    & $MysqlDump "--host=$($db.DB_HOST)" "--port=$($db.DB_PORT)" "--user=$($db.DB_USERNAME)" --single-transaction --routines --triggers `
        --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 "--result-file=$file" $db.DB_DATABASE
    if ($LASTEXITCODE -ne 0) { throw "mysqldump gagal (exit $LASTEXITCODE)" }
} finally { $env:MYSQL_PWD = $previous }
# Dump yang terpotong tidak punya penanda ini; jangan dihitung sebagai cadangan.
if (-not (Select-String -LiteralPath $file -Pattern '^-- Dump completed' -Quiet)) { Remove-Item -LiteralPath $file; throw 'Dump tidak lengkap' }
$hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLower()
Set-Content -LiteralPath "$file.sha256" -Value "$hash  $(Split-Path $file -Leaf)" -Encoding ascii
Get-ChildItem -LiteralPath $OutDir -File | Where-Object { $_.Name -like "$($db.DB_DATABASE)-*.sql*" -and $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } | Remove-Item
"$file`n$hash"
