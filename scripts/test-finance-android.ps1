param([string]$JavaHome='', [string]$AdbPath='')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
if (-not $AdbPath) {
    $sdkDirectory=$env:ANDROID_SDK_ROOT
    if (-not $sdkDirectory) { $sdkDirectory=$env:ANDROID_HOME }
    if (-not $sdkDirectory) {
        $sdkProperty=Get-Content -LiteralPath (Join-Path $projectRoot 'android/local.properties') | Where-Object { $_.StartsWith('sdk.dir=') } | Select-Object -First 1
        if ($sdkProperty) { $sdkDirectory=$sdkProperty.Substring(8).Replace('\:',':').Replace('\\','\') }
    }
    if ($sdkDirectory) { $AdbPath=Join-Path $sdkDirectory 'platform-tools/adb.exe' }
    else { $AdbPath=(Get-Command adb -ErrorAction Stop).Source }
}
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'Pass -AdbPath for the Android SDK adb executable.' }
$evidenceDirectory=Join-Path $projectRoot 'outputs/finance-android'
New-Item -ItemType Directory -Force $evidenceDirectory | Out-Null
& (Join-Path $PSScriptRoot 'build-android.ps1') -JavaHome $JavaHome -Tasks @(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug',':app:connectedDebugAndroidTest','-PfinanceApp','-Pandroid.testInstrumentationRunnerArguments.class=com.inofarm.jogja.FinanceLocalStoreTest,com.inofarm.jogja.FinanceSyncCoordinatorTest')
foreach ($apk in @('android/app/build/outputs/apk/debug/app-debug.apk','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $AdbPath install -r -t (Join-Path $projectRoot $apk)
    if ($LASTEXITCODE -ne 0) { throw 'Isolated finance test APK installation failed.' }
}
function Invoke-FinanceInstrumentation([string]$Class,[string]$Phase,[string]$LogName) {
    $instrumentArguments=@('shell','am','instrument','-w','-r','-e','class',$Class)
    if ($Phase) { $instrumentArguments+=@('-e','survivalPhase',$Phase) }
    $instrumentArguments+='com.inofarm.jogja.finance.test/androidx.test.runner.AndroidJUnitRunner'
    $instrumentOutput=& $AdbPath @instrumentArguments
    $instrumentOutput | Set-Content -LiteralPath (Join-Path $evidenceDirectory $LogName)
    if (($instrumentOutput -join "`n") -notmatch 'OK \(1 test\)') { throw "Finance instrumentation failed: $Class" }
}
Invoke-FinanceInstrumentation 'com.inofarm.jogja.FinanceProcessSurvivalTest#prepare' 'prepare' 'process-prepare.txt'
# Only the separate synthetic test app is stopped. No pm clear/uninstall/user database operation.
& $AdbPath shell am force-stop com.inofarm.jogja.finance
Invoke-FinanceInstrumentation 'com.inofarm.jogja.FinanceProcessSurvivalTest#verify' 'verify' 'process-verify.txt'
Invoke-FinanceInstrumentation 'com.inofarm.jogja.FinanceStorageTimingTest' '' 'storage-timing.txt'
& $AdbPath pull '/sdcard/Android/data/com.inofarm.jogja.finance/files/finance-storage-timings.csv' (Join-Path $evidenceDirectory 'storage-timings.csv')
if ($LASTEXITCODE -ne 0) { throw 'Timing evidence pull failed.' }
Write-Output 'Finance local checks passed. Storage timing is not end-to-end UI performance acceptance.'
