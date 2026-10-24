param([int]$Port=8001)
$projectRoot=Split-Path $PSScriptRoot -Parent
$phpPath=Join-Path $projectRoot '.tools/php/php.exe'
Push-Location (Join-Path $projectRoot 'backend')
try { & $phpPath artisan serve --env=testing --host=127.0.0.1 "--port=$Port" } finally { Pop-Location }
