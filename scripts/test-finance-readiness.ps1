#requires -Version 7.0
param(
    [string]$JavaHome='', [string]$PhpPath='', [string]$PythonPath='',
    [string]$SdkPath="$env:LOCALAPPDATA/Android/Sdk", [string]$Device='',
    [string]$BackendFilter='^(?!.*DemoHistoryTest)',
    [switch]$SkipBackend, [switch]$SkipBuild, [switch]$AuditDependencies
)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(-not $PhpPath) {$PhpPath=Join-Path $project '.tools/php/php.exe';if(-not(Test-Path -LiteralPath $PhpPath)){$PhpPath=(Get-Command php -ErrorAction Stop).Source}}
if(-not $PythonPath) {$PythonPath=Join-Path $project '.tools/finance-contracts-venv/Scripts/python.exe';if(-not(Test-Path -LiteralPath $PythonPath)){$PythonPath=(Get-Command python -ErrorAction Stop).Source}}
$PhpPath=(Resolve-Path -LiteralPath $PhpPath).Path
$PythonPath=(Resolve-Path -LiteralPath $PythonPath).Path
$directory=Join-Path $project ('outputs/finance-readiness/'+(Get-Date -Format 'yyyyMMdd-HHmmss')+'-'+([guid]::NewGuid().ToString('N').Substring(0,8)))
New-Item -ItemType Directory -Path $directory | Out-Null
$summary=[ordered]@{commit=(& git -C $project rev-parse HEAD);source_dirty=([bool](& git -C $project status --porcelain --untracked-files=no));started_wib=([TimeZoneInfo]::ConvertTimeBySystemTimeZoneId([datetime]::UtcNow,'SE Asia Standard Time').ToString('o'));status='RUNNING';stages=@()}
function Invoke-Stage([string]$Name,[scriptblock]$Action) {
    $timer=[Diagnostics.Stopwatch]::StartNew();$log=Join-Path $directory ($Name+'.log');$global:LASTEXITCODE=0
    try {
        & $Action *> $log
        if($LASTEXITCODE -ne 0){throw "Exit code $LASTEXITCODE"}
        $summary.stages+=@{name=$Name;status='PASS';seconds=[math]::Round($timer.Elapsed.TotalSeconds,3);log=$log}
        Write-Output "PASS $Name"
    } catch {
        $summary.stages+=@{name=$Name;status='FAIL';seconds=[math]::Round($timer.Elapsed.TotalSeconds,3);log=$log}
        throw "$Name failed. Inspect $log. $($_.Exception.Message)"
    }
}
Push-Location $project
try {
    Invoke-Stage contracts {& $PythonPath -X utf8 -B scripts/check-finance-contracts.py}
    Invoke-Stage context {& $PythonPath -X utf8 -B scripts/check-finance-context.py}
    if($SkipBackend) {$summary.stages+=@{name='backend';status='SKIPPED';reason='Explicit SkipBackend'}}
    else {
        if(Get-ChildItem -LiteralPath (Join-Path $project 'tmp') -Filter 'finance-*-fixture.json' -File -ErrorAction SilentlyContinue){throw 'Live fixture manifest exists; finish its owner run before PHPUnit.'}
        if(Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue){throw 'Port8001 is occupied; no backend test/reset while integration HTTP may be active.'}
        Invoke-Stage database-guard {& $PhpPath backend/vendor/phpunit/phpunit/phpunit -c backend/phpunit.xml backend/tests/Unit/TestDatabaseGuardTest.php --testdox}
        Write-Output "Backend filter: $BackendFilter (default excludes historical DemoHistory tests explicitly)."
        Invoke-Stage backend {
            Push-Location backend
            try {& $PhpPath vendor/phpunit/phpunit/phpunit --filter $BackendFilter --testdox} finally {Pop-Location}
        }
    }
    if($SkipBuild) {$summary.stages+=@{name='android-build';status='SKIPPED';reason='Explicit SkipBuild'}}
    else {Invoke-Stage android-build {
        & scripts/build-android.ps1 -JavaHome $JavaHome -SdkPath $SdkPath -Tasks @('-PfinanceApp',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:testDebugUnitTest',':app:lintDebug')
    }}
    if($Device) {
        $adb=Join-Path $SdkPath 'platform-tools/adb.exe'
        Invoke-Stage device {
            foreach($apk in @('android/app/build/outputs/apk/debug/app-debug.apk','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
                & $adb -s $Device install -r -t $apk
                if($LASTEXITCODE -ne 0){throw 'Isolated finance APK install failed.'}
            }
            & $adb -s $Device shell input keyevent KEYCODE_WAKEUP
            & $adb -s $Device shell wm dismiss-keyguard
            $apiLevel=& $adb -s $Device shell getprop ro.build.version.sdk
            if([int]$apiLevel -ge 37) {& $adb -s $Device shell pm grant com.inofarm.jogja.finance android.permission.ACCESS_LOCAL_NETWORK}
            $classes='com.inofarm.jogja.FinanceAdminTest,com.inofarm.jogja.FinanceAdminUiTest,com.inofarm.jogja.FinanceLocalStoreTest,com.inofarm.jogja.FinanceSyncCoordinatorTest,com.inofarm.jogja.FinanceSyncScheduleTest,com.inofarm.jogja.FinanceRecoveryTest,com.inofarm.jogja.FinanceInvestmentTest,com.inofarm.jogja.FinanceSeasonReportTest,com.inofarm.jogja.FinanceReportExportTest,com.inofarm.jogja.FinanceStorageTimingTest'
            $stayon=(& $adb -s $Device shell settings get global stay_on_while_plugged_in).Trim()
            try {
                & $adb -s $Device shell svc power stayon true
                $result=& $adb -s $Device shell am instrument -w -r -e class $classes com.inofarm.jogja.finance.test/androidx.test.runner.AndroidJUnitRunner
            } finally {
                if($stayon -match '^\d+$') {& $adb -s $Device shell settings put global stay_on_while_plugged_in $stayon}
                else {& $adb -s $Device shell settings delete global stay_on_while_plugged_in}
            }
            $result
            if(($result -join "`n") -notmatch 'OK \(\d+ tests?\)' -or ($result -join "`n") -match 'FAILURES|INSTRUMENTATION_FAILED'){throw 'Instrumentation did not pass; adb exit code alone is insufficient.'}
            & $adb -s $Device pull /sdcard/Android/data/com.inofarm.jogja.finance/files/finance-storage-timings.csv (Join-Path $directory 'storage-timings.csv')
        }
    } else {$summary.stages+=@{name='device';status='SKIPPED';reason='No explicit device selected'}}
    if($AuditDependencies) {Invoke-Stage dependency-audit {
        $composer=Join-Path $project '.tools/composer.phar'
        if(-not(Test-Path -LiteralPath $composer)){throw 'Composer phar required at .tools/composer.phar for this optional stage.'}
        & $PhpPath $composer --working-dir=backend audit --locked
    }}
    $summary.status='PASSED_SELECTED_CHECKS'
    Write-Output 'Selected engineering checks passed. Skipped checks, UAT/pentest and cutover remain separate gates.'
} catch {$summary.status='FAILED';throw}
finally {
    $summary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $directory 'summary.json') -Encoding utf8
    Pop-Location
    Write-Output "Evidence: $directory"
}
