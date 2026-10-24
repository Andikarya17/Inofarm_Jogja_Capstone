param([switch]$GuardOnly)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$phpPath = Join-Path $projectRoot '.tools/php/php.exe'
if (-not (Test-Path $phpPath)) { $phpPath = (Get-Command php -ErrorAction Stop).Source }
Push-Location (Join-Path $projectRoot 'backend')
try {
    & $phpPath vendor/phpunit/phpunit/phpunit tests/Unit/TestDatabaseGuardTest.php --testdox
    if ($LASTEXITCODE -ne 0) { throw 'Isolated database guard tests failed; database tests were not started' }
    if ($GuardOnly) { return }
    & $phpPath vendor/phpunit/phpunit/phpunit --testdox
    if ($LASTEXITCODE -ne 0) { throw 'Backend tests failed' }
} finally { Pop-Location }
