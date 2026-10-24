param([int]$Port=8000,[string]$BindAddress='127.0.0.1')
$projectRoot = Split-Path $PSScriptRoot -Parent
$phpPath = Join-Path $projectRoot '.tools/php/php.exe'
if (-not (Test-Path $phpPath)) { $phpPath = (Get-Command php -ErrorAction Stop).Source }
Push-Location (Join-Path $projectRoot 'backend')
try { & $phpPath artisan serve "--host=$BindAddress" "--port=$Port" } finally { Pop-Location }
