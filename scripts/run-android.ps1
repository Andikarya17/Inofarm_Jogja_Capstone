param(
    [string]$SdkPath = "$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaHome = 'C:/Program Files/Android/Android Studio/jbr',
    [string]$StudioPath = 'C:/Program Files/Android/Android Studio/bin/studio64.exe',
    [string]$Avd = 'Medium_Phone_API_37.0',
    [string]$Serial = '',
    [switch]$SkipStudio,
    [switch]$SkipBackend
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$runtimeDir = Join-Path $projectRoot '.runtime'
New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
Start-Transcript -Path (Join-Path $runtimeDir 'android-launcher.log') -Force | Out-Null
try {
$androidRoot = Join-Path $projectRoot 'android'
$adb = Join-Path $SdkPath 'platform-tools/adb.exe'
$emulator = Join-Path $SdkPath 'emulator/emulator.exe'
foreach ($required in @($adb, (Join-Path $JavaHome 'bin/java.exe'), (Join-Path $SdkPath 'platforms/android-37.0/android.jar'))) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Komponen belum tersedia: $required. Pasang melalui Android Studio SDK Manager atau berikan -SdkPath/-JavaHome." }
}
function Invoke-Adb {
    param([string[]]$AdbArguments)
    & $adb -s $script:deviceSerial @AdbArguments
    if ($LASTEXITCODE -ne 0) { throw "ADB gagal: $($AdbArguments -join ' ')" }
}
if (-not $SkipStudio) {
    if (-not (Test-Path -LiteralPath $StudioPath)) { throw 'Android Studio tidak ditemukan. Gunakan -StudioPath atau -SkipStudio.' }
    # Open the actual Gradle root, rather than the repository's generic IDE project.
    Start-Process -FilePath $StudioPath -ArgumentList ('"' + $androidRoot + '"') -WindowStyle Normal | Out-Null
}
& $adb start-server
if ($LASTEXITCODE -ne 0) { throw 'ADB server gagal dimulai.' }
$connected = @(& $adb devices | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
if ($Serial) { $script:deviceSerial = $Serial }
elseif ($connected.Count -eq 1) { $script:deviceSerial = $connected[0] }
elseif ($connected.Count -gt 1) { throw 'Lebih dari satu perangkat aktif. Jalankan dengan -Serial dari adb devices.' }
else {
    if (-not (Test-Path -LiteralPath $emulator)) { throw 'Android Emulator belum terpasang melalui SDK Manager.' }
    $available = @(& $emulator -list-avds)
    if ($Avd -notin $available) { throw "AVD $Avd belum tersedia. Buat lewat Device Manager. Tersedia: $($available -join ', ')" }
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $Avd) -WindowStyle Normal | Out-Null
    $deadline = [DateTime]::UtcNow.AddMinutes(4)
    do {
        Start-Sleep -Seconds 2
        $connected = @(& $adb devices | Where-Object { $_ -match '^emulator-\d+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
    } until ($connected.Count -gt 0 -or [DateTime]::UtcNow -gt $deadline)
    if ($connected.Count -ne 1) { throw 'Emulator belum siap atau ambigu. Periksa Device Manager, lalu ulangi.' }
    $script:deviceSerial = $connected[0]
}
$deadline = [DateTime]::UtcNow.AddMinutes(4)
Write-Host "Menunggu Android siap: $script:deviceSerial"
do {
    $boot = & $adb -s $script:deviceSerial shell getprop sys.boot_completed 2>$null
    if ($boot -eq '1') { break }
    if ([DateTime]::UtcNow -gt $deadline) { throw 'Perangkat belum selesai boot. Periksa emulator/USB debugging.' }
    Start-Sleep -Seconds 2
} while ($true)
if (-not $SkipBackend) {
    Write-Host 'Menyiapkan MySQL lokal melalui script existing...'
    & (Join-Path $PSScriptRoot 'local-database.ps1')
    $healthy = $false
    try { $healthy = (Invoke-WebRequest 'http://127.0.0.1:8000/up' -TimeoutSec 3).StatusCode -eq 200 } catch {}
    if (-not $healthy) {
        $listener = Get-NetTCPConnection -LocalPort 8000 -State Listen -ErrorAction SilentlyContinue
        if ($listener) { throw 'Port 8000 sedang dipakai namun health API gagal. Periksa server sebelum menjalankan ulang.' }
        $php = Join-Path $projectRoot '.tools/php/php.exe'
        if (-not (Test-Path -LiteralPath $php)) { throw 'PHP lokal tidak ditemukan. Untuk menjalankan APK saja gunakan -SkipBackend.' }
        $runtimeDir = Join-Path $projectRoot '.runtime'
        New-Item -ItemType Directory -Force -Path $runtimeDir | Out-Null
        Start-Process -FilePath $php -WorkingDirectory (Join-Path $projectRoot 'backend') -ArgumentList @('artisan', 'serve', '--host=127.0.0.1', '--port=8000') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $runtimeDir 'android-backend.stdout.log') -RedirectStandardError (Join-Path $runtimeDir 'android-backend.stderr.log') | Out-Null
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            Start-Sleep -Seconds 1
            try { $healthy = (Invoke-WebRequest 'http://127.0.0.1:8000/up' -TimeoutSec 2).StatusCode -eq 200 } catch {}
            if ($healthy) { break }
        }
        if (-not $healthy) { throw 'Backend gagal siap. Periksa .runtime/android-backend.stderr.log.' }
    }
}
Write-Host 'Build APK debug...'
$loginUrl = if ($script:deviceSerial -like 'emulator-*') { 'http://10.0.2.2:8000/api/v1/' } else { 'http://127.0.0.1:8000/api/v1/' }
& (Join-Path $PSScriptRoot 'build-android.ps1') -Tasks @(':app:assembleDebug', "-PinofarmDebugApiUrl=$loginUrl") -JavaHome $JavaHome -SdkPath $SdkPath
Invoke-Adb -AdbArguments @('reverse', 'tcp:8000', 'tcp:8000')
Invoke-Adb -AdbArguments @('install', '-r', (Join-Path $androidRoot 'app/build/outputs/apk/debug/app-debug.apk'))
if ($script:deviceSerial -like 'emulator-*') { Invoke-Adb -AdbArguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') }
Invoke-Adb -AdbArguments @('shell', 'am', 'start', '-W', '-n', 'com.inofarm.jogja/.MainActivity')
if ($script:deviceSerial -like 'emulator-*') {
    Write-Host 'Untuk melihat emulator di Android Studio: View > Tool Windows > Running Devices.'
    Write-Host 'Pilih tab Medium Phone API 37.0. Device Manager hanya daftar perangkat.'
}
Write-Host "Aplikasi terbuka. Alamat server debug otomatis: $loginUrl"
Write-Host 'Gunakan akun owner Anda. Launcher tidak membuat akun atau mengubah konfigurasi produksi.'
} finally { Stop-Transcript | Out-Null }
