param([Parameter(Mandatory=$true)][string]$JavaHome,[string]$SdkPath="$env:LOCALAPPDATA/Android/Sdk",[switch]$SkipChecks)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $SdkPath 'platform-tools/adb.exe'
$php=Join-Path $projectRoot '.tools/php/php.exe'
$manifest=Join-Path $projectRoot 'tmp/finance-device-fixture.json'
$outputDirectory=Join-Path $projectRoot 'outputs/finance-entry'
if(Test-Path -LiteralPath $manifest) {throw 'A synthetic fixture already exists; verify its provenance/cleanup before starting another.'}
if(Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue) {throw 'Port8001 is occupied; refusing to replace the service.'}
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
$tasks=if($SkipChecks) {@(':app:assembleDebug',':app:assembleDebugAndroidTest','-PfinanceApp')} else {@(':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug',':app:connectedDebugAndroidTest','-PfinanceApp','-Pandroid.testInstrumentationRunnerArguments.class=com.inofarm.jogja.FinanceLocalStoreTest,com.inofarm.jogja.FinanceSyncCoordinatorTest,com.inofarm.jogja.FinanceEntryUiTest')}
& (Join-Path $PSScriptRoot 'build-android.ps1') -JavaHome $JavaHome -Tasks $tasks
foreach($apk in @('android/app/build/outputs/apk/debug/app-debug.apk','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
    & $adb install -r -t (Join-Path $projectRoot $apk)
    if($LASTEXITCODE -ne 0) {throw 'Finance test APK install failed.'}
}
$apiLevel=& $adb shell getprop ro.build.version.sdk
if([int]$apiLevel -ge 37) {& $adb shell pm grant com.inofarm.jogja.finance android.permission.ACCESS_LOCAL_NETWORK}
$serverProcess=$null
$listenerPid=$null
$listenerWrapperPid=$null
$fixtureCreated=$false
function Start-TestApi {
    $script:serverProcess=Start-Process -FilePath $php -WorkingDirectory (Join-Path $projectRoot 'backend') -ArgumentList @('artisan','serve','--env=testing','--host=127.0.0.1','--port=8001') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputDirectory 'server.stdout.txt') -RedirectStandardError (Join-Path $outputDirectory 'server.stderr.txt')
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    do {
        try {
            if((Invoke-WebRequest 'http://127.0.0.1:8001/up' -TimeoutSec 1).StatusCode -eq 200) {
                $listener=Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction Stop | Select-Object -First 1
                $owner=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
                if($owner.Name -ne 'php.exe' -or -not $owner.CommandLine.Contains($projectRoot) -or -not $owner.CommandLine.Contains('127.0.0.1:8001')) {throw 'Listener is not this workspace test PHP server.'}
                $script:listenerPid=$owner.ProcessId
                $wrapper=Get-CimInstance Win32_Process -Filter "ProcessId=$($owner.ParentProcessId)"
                $script:listenerWrapperPid=if($wrapper.Name -eq 'cmd.exe' -and $wrapper.CommandLine.Contains($projectRoot) -and $wrapper.CommandLine.Contains('127.0.0.1:8001')) {$wrapper.ProcessId} else {$null}
                return
            }
        } catch {};Start-Sleep -Milliseconds 200
    } while([DateTime]::UtcNow -lt $deadline)
    throw 'Guarded test API did not become ready.'
}
function Stop-TestApi {
    if($script:serverProcess -and -not $script:serverProcess.HasExited) {
        $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:serverProcess.Id)"
        if(-not ($owned.CommandLine.Contains('--env=testing') -and $owned.CommandLine.Contains('--port=8001'))) {throw 'Test API process ownership changed.'}
        Stop-Process -Id $owned.ProcessId
    }
    if($script:listenerWrapperPid) {
        $wrapper=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:listenerWrapperPid)"
        if($wrapper) {
            if($wrapper.Name -ne 'cmd.exe' -or -not $wrapper.CommandLine.Contains($projectRoot) -or -not $wrapper.CommandLine.Contains('127.0.0.1:8001')) {throw 'Test PHP wrapper ownership changed.'}
            Stop-Process -Id $wrapper.ProcessId
        }
    }
    if($script:listenerPid) {
        $listener=Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
        if($listener) {
            if($listener.OwningProcess -ne $script:listenerPid) {throw 'Test port ownership changed; refusing to stop another service.'}
            $owner=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:listenerPid)"
            if($owner.Name -ne 'php.exe' -or -not $owner.CommandLine.Contains($projectRoot) -or -not $owner.CommandLine.Contains('127.0.0.1:8001')) {throw 'Test listener ownership changed.'}
            Stop-Process -Id $owner.ProcessId
        }
        $deadline=[DateTime]::UtcNow.AddSeconds(5)
        while(Get-NetTCPConnection -LocalPort 8001 -State Listen -ErrorAction SilentlyContinue) {
            if([DateTime]::UtcNow -gt $deadline) {throw 'Test API listener did not stop; offline precondition failed.'}
            Start-Sleep -Milliseconds 100
        }
    }
    $script:serverProcess=$null
    $script:listenerPid=$null
    $script:listenerWrapperPid=$null
}
function Assert-Instrumentation([Diagnostics.Process]$Process,[string]$LogPath) {
    if(-not $Process.WaitForExit(60000)) {Stop-Process -Id $Process.Id;throw 'Finance instrumentation timeout; inspect ignored test logs.'}
    if((Get-Content -LiteralPath $LogPath -Raw) -notmatch 'OK \(1 test\)') {throw "Finance instrumentation failed; inspect $LogPath"}
}
try {
    & $php (Join-Path $PSScriptRoot 'finance-device-fixture.php') create
    if($LASTEXITCODE -ne 0) {throw 'Guarded fixture creation failed.'}
    $fixtureCreated=$true
    $fixture=Get-Content -LiteralPath $manifest -Raw | ConvertFrom-Json
    & $adb push $manifest '/sdcard/Android/data/com.inofarm.jogja.finance/files/finance-device-fixture.json'
    Start-TestApi
    $queueLog=Join-Path $outputDirectory 'device-queue.txt'
    $queueProcess=Start-Process -FilePath $adb -ArgumentList @('shell','am','instrument','-w','-r','-e','class','com.inofarm.jogja.FinanceBackendDeviceTest#queueWithBackendOffline','-e','devicePhase','queue','com.inofarm.jogja.finance.test/androidx.test.runner.AndroidJUnitRunner') -WindowStyle Hidden -PassThru -RedirectStandardOutput $queueLog -RedirectStandardError (Join-Path $outputDirectory 'device-queue.stderr.txt')
    $deadline=[DateTime]::UtcNow.AddSeconds(60)
    do {
        $ready=& $adb shell cat '/sdcard/Android/data/com.inofarm.jogja.finance/files/finance-device-ready.txt' 2>$null
        if($LASTEXITCODE -eq 0 -and ($ready -join '').Trim() -eq $fixture.fixture_id) {break}
        if($queueProcess.HasExited -or [DateTime]::UtcNow -gt $deadline) {throw 'Device did not reach the offline barrier.'}
        Start-Sleep -Milliseconds 200
    } while($true)
    Stop-TestApi
    $signal=Join-Path $outputDirectory 'go-offline.txt'
    Set-Content -LiteralPath $signal -Value $fixture.fixture_id -NoNewline
    & $adb push $signal '/sdcard/Android/data/com.inofarm.jogja.finance/files/finance-device-go-offline.txt'
    Assert-Instrumentation $queueProcess $queueLog
    & $adb shell am force-stop com.inofarm.jogja.finance
    Start-TestApi
    $verifyLog=Join-Path $outputDirectory 'device-verify.txt'
    $verifyProcess=Start-Process -FilePath $adb -ArgumentList @('shell','am','instrument','-w','-r','-e','class','com.inofarm.jogja.FinanceBackendDeviceTest#reconnectReplayAndManagerReview','-e','devicePhase','verify','com.inofarm.jogja.finance.test/androidx.test.runner.AndroidJUnitRunner') -WindowStyle Hidden -PassThru -RedirectStandardOutput $verifyLog -RedirectStandardError (Join-Path $outputDirectory 'device-verify.stderr.txt')
    Assert-Instrumentation $verifyProcess $verifyLog
    & $php (Join-Path $PSScriptRoot 'finance-device-fixture.php') verify
    if($LASTEXITCODE -ne 0) {throw 'MySQL verification failed.'}
} finally {
    Stop-TestApi
    if($fixtureCreated -and (Test-Path -LiteralPath $manifest)) {& $php (Join-Path $PSScriptRoot 'finance-device-fixture.php') cleanup;if($LASTEXITCODE -ne 0) {throw 'Synthetic fixture cleanup failed.'}}
}
Write-Output 'PASS: real guarded MySQL/Laravel worker activity + multi-grade harvest offline -> process restart -> receipts/replays -> manager review/cancel -> old ACK cannot downgrade. No user DB or legacy app data reset.'
