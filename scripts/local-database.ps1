param([string]$MySqlBin = 'C:/laragon/bin/mysql/mysql-8.0.30-winx64/bin')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.runtime/mysql'
$dataDir = Join-Path $runtimeDir 'data'
New-Item -ItemType Directory -Force $runtimeDir | Out-Null
$mySqlServer = Join-Path $MySqlBin 'mysqld.exe'
if (-not (Test-Path (Join-Path $dataDir 'mysql'))) {
    & $mySqlServer --no-defaults "--basedir=$(Split-Path $MySqlBin -Parent)" "--datadir=$dataDir" --initialize-insecure --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed' }
}
$tcpClient = [System.Net.Sockets.TcpClient]::new()
try { $tcpClient.Connect('127.0.0.1',3307); $running = $true } catch { $running = $false } finally { $tcpClient.Dispose() }
if (-not $running) {
    $argsList = @('--no-defaults', "--basedir=`"$(Split-Path $MySqlBin -Parent)`"", "--datadir=`"$dataDir`"", '--port=3307', '--bind-address=127.0.0.1', "--log-error=`"$(Join-Path $runtimeDir 'mysql.log')`"")
    $dbProcess = Start-Process -FilePath $mySqlServer -ArgumentList $argsList -PassThru -WindowStyle Hidden
    $dbProcess.Id | Set-Content (Join-Path $runtimeDir 'pid')
    Start-Sleep -Seconds 3
}
else {
    $pidPath=Join-Path $runtimeDir 'pid'
    if (-not (Test-Path $pidPath)) { throw 'Port 3307 already in use; refusing to touch an unowned MySQL instance.' }
    $ownedProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$(Get-Content $pidPath)"
    if (-not $ownedProcess -or $ownedProcess.Name -ne 'mysqld.exe' -or -not $ownedProcess.CommandLine.Contains($dataDir)) {
        throw 'Port 3307 ownership cannot be verified; use manual setup.'
    }
}
$credentialsPath = Join-Path $runtimeDir 'client.cnf'
if (-not (Test-Path $credentialsPath)) {
    if ((Test-Path (Join-Path $projectRoot 'backend/.env')) -or (Test-Path (Join-Path $projectRoot 'backend/.env.testing'))) { throw 'Existing backend environment found; refusing to overwrite it. Configure MySQL manually.' }
    $randomBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $dbPassword = [Convert]::ToHexString($randomBytes)
    [Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
    $testDbPassword = [Convert]::ToHexString($randomBytes)
    $sqlFile = Join-Path $runtimeDir 'bootstrap.sql'
    @"
CREATE DATABASE IF NOT EXISTS inofarm_local CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS inofarm_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'inofarm'@'127.0.0.1' IDENTIFIED BY '$dbPassword';
GRANT ALL ON inofarm_local.* TO 'inofarm'@'127.0.0.1';
CREATE USER IF NOT EXISTS 'inofarm_test_runner'@'127.0.0.1' IDENTIFIED BY '$testDbPassword';
GRANT ALL ON inofarm_test.* TO 'inofarm_test_runner'@'127.0.0.1';
ALTER USER 'root'@'localhost' IDENTIFIED BY '$dbPassword';
"@ | Set-Content $sqlFile -Encoding utf8
    Get-Content $sqlFile -Raw | & (Join-Path $MySqlBin 'mysql.exe') --no-defaults --host=127.0.0.1 --port=3307 --user=root
    if ($LASTEXITCODE -ne 0) { throw 'MySQL schema provisioning failed' }
    "[client]`nuser=inofarm`npassword=$dbPassword`nhost=127.0.0.1`nport=3307" | Set-Content $credentialsPath
    $appKeyBytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($appKeyBytes)
    $appKey = [Convert]::ToBase64String($appKeyBytes)
    foreach ($dbName in @('inofarm_local','inofarm_test')) {
        $envName = if ($dbName -eq 'inofarm_test') { '.env.testing' } else { '.env' }
        $appEnv = if ($dbName -eq 'inofarm_test') { 'testing' } else { 'local' }
        $environmentUser = if ($dbName -eq 'inofarm_test') { 'inofarm_test_runner' } else { 'inofarm' }
        $environmentPassword = if ($dbName -eq 'inofarm_test') { $testDbPassword } else { $dbPassword }
        @"
APP_NAME="Inofarm Jogja"
APP_ENV=$appEnv
APP_KEY=base64:$appKey
APP_DEBUG=false
APP_URL=http://127.0.0.1:8000
DB_CONNECTION=mysql
DB_HOST=127.0.0.1
DB_PORT=3307
DB_DATABASE=$dbName
DB_USERNAME=$environmentUser
DB_PASSWORD=$environmentPassword
CACHE_STORE=file
SESSION_DRIVER=file
QUEUE_CONNECTION=sync
LOG_CHANNEL=single
"@ | Set-Content (Join-Path $projectRoot "backend/$envName")
    }
    Remove-Item -LiteralPath $sqlFile
}
Write-Output 'Dedicated MySQL running on 127.0.0.1:3307; schemas inofarm_local/inofarm_test.'
