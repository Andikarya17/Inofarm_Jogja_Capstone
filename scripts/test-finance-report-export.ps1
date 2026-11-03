#requires -Version 7.0
param([Parameter(Mandatory=$true)][string]$JavaHome,[string]$SdkPath="$env:LOCALAPPDATA/Android/Sdk",[string]$PhpPath,[string]$GradleHome,[string]$Device='emulator-5554',[switch]$RebootBetweenPhases,[switch]$SkipBuild,[switch]$UseAdbReverse)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $SdkPath 'platform-tools/adb.exe'
$php=if($PhpPath) {$PhpPath} else {Join-Path $projectRoot '.tools/php/php.exe'}
$manifest=Join-Path $projectRoot 'tmp/finance-report-export-fixture.json'
$outputDirectory=Join-Path $projectRoot 'outputs/finance-report-exports'
if(Test-Path -LiteralPath $manifest) {throw 'Existing report fixture: inspect ownership before rerunning.'}
$portProbe=[Net.Sockets.TcpClient]::new()
try {
    try {$connecting=$portProbe.ConnectAsync('127.0.0.1',8001);$occupied=$connecting.Wait(500) -and $portProbe.Connected} catch {$occupied=$false}
    if($occupied) {throw 'Port8001 occupied: refusing to replace another service.'}
} finally {$portProbe.Dispose()}
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
New-Item -ItemType Directory -Force (Join-Path $projectRoot '.runtime') | Out-Null
$probeNonce=[Guid]::NewGuid().ToString('N')
$probeRouter=Join-Path $projectRoot ('.runtime/finance-report-export-router-'+$probeNonce+'.php')
$laravelRouter=(Join-Path $projectRoot 'backend/vendor/laravel/framework/src/Illuminate/Foundation/resources/server.php').Replace('\','/')
$router='<?php
if (parse_url($_SERVER[''REQUEST_URI''], PHP_URL_PATH) === ''/__report_fixture_probe/__NONCE__'') {
    header(''Content-Type: application/json'');header(''Cache-Control: no-store'');
    echo json_encode([''nonce''=>''__NONCE__'',''pid''=>getmypid()]);return;
}
require ''__LARAVEL_ROUTER__'';
'
[IO.File]::WriteAllText($probeRouter,$router.Replace('__NONCE__',$probeNonce).Replace('__LARAVEL_ROUTER__',$laravelRouter),[Text.UTF8Encoding]::new($false))
$oldAppEnv=$env:APP_ENV;$env:APP_ENV='testing'
$oldGradleHome=$env:GRADLE_USER_HOME
if($GradleHome) {$env:GRADLE_USER_HOME=$GradleHome}
$serverProcess=$null;$phaseProcess=$null;$logProcess=$null;$fixtureCreated=$false;$reverseCreated=$false
function Start-TestApi {
    $script:serverProcess=Start-Process -FilePath $php -WorkingDirectory (Join-Path $projectRoot 'backend/public') -ArgumentList @('-S','127.0.0.1:8001','-t',(Join-Path $projectRoot 'backend/public'),$probeRouter) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputDirectory 'server.stdout.txt') -RedirectStandardError (Join-Path $outputDirectory 'server.stderr.txt')
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    do {
        if($script:serverProcess.HasExited) {throw 'Owned test PHP exited before readiness; refusing to use another service.'}
        try {
            $probe=Invoke-RestMethod ('http://127.0.0.1:8001/__report_fixture_probe/'+$probeNonce) -NoProxy -TimeoutSec 3
            if($probe.nonce -ne $probeNonce -or $probe.pid -ne $script:serverProcess.Id) {throw 'Test API nonce/PID does not match the process just started.'}
            $owner=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:serverProcess.Id)"
            if($owner.Name -ne 'php.exe' -or -not $owner.CommandLine.Contains($probeRouter) -or -not $owner.CommandLine.Contains('127.0.0.1:8001')) {throw 'Test PHP ownership mismatch.'}
            return
        } catch {$script:lastReadinessError=$_.Exception.Message}
        Start-Sleep -Milliseconds 200
    } while([DateTime]::UtcNow -lt $deadline)
    throw ('Guarded test API readiness failed: '+$script:lastReadinessError)
}
function Stop-TestApi {
    if($script:serverProcess -and -not $script:serverProcess.HasExited) {
        $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:serverProcess.Id)"
        if($owned.Name -ne 'php.exe' -or -not $owned.CommandLine.Contains($probeRouter) -or -not $owned.CommandLine.Contains('127.0.0.1:8001')) {throw 'Test ownership changed; refusing to stop another process.'}
        Stop-Process -Id $owned.ProcessId
        if(-not $script:serverProcess.WaitForExit(5000)) {throw 'Owned PHP did not stop; offline precondition failed.'}
    }
    $script:serverProcess=$null
}
function Stop-TestLog {
    if($script:logProcess -and -not $script:logProcess.HasExited) {
        $owned=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:logProcess.Id)"
        if($owned.Name -ne 'adb.exe' -or -not $owned.CommandLine.Contains($Device) -or -not $owned.CommandLine.Contains('logcat')) {throw 'Log capture ownership changed.'}
        Stop-Process -Id $owned.ProcessId
    }
    $script:logProcess=$null
}
function Assert-Instrumentation([Diagnostics.Process]$Process,[string]$LogPath) {
    $deadline=[DateTime]::UtcNow.AddSeconds(180)
    while(-not $Process.WaitForExit(2000)) {if([DateTime]::UtcNow -gt $deadline) {throw 'Finance instrumentation timeout; owned phase will stop before fixture cleanup.'}}
    if((Get-Content -LiteralPath $LogPath -Raw) -notmatch 'OK \(1 test\)') {throw "Finance instrumentation failed; inspect $LogPath"}
}

try {
    if(-not $SkipBuild) {
    & (Join-Path $JavaHome 'bin/java.exe') --enable-native-access=ALL-UNNAMED -classpath (Join-Path $projectRoot 'android/gradle/wrapper/gradle-wrapper.jar') org.gradle.wrapper.GradleWrapperMain -p (Join-Path $projectRoot 'android') :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --max-workers=2 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process'
    if($LASTEXITCODE -ne 0) {throw 'Finance report APK build failed.'}
    }
    foreach($apk in @('android/app/build/outputs/apk/debug/app-debug.apk','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $adb -s $Device install -r -t (Join-Path $projectRoot $apk)
        if($LASTEXITCODE -ne 0) {throw 'Finance APK install failed.'}
    }
    $apiLevel=& $adb -s $Device shell getprop ro.build.version.sdk
    if([int]$apiLevel -ge 37) {& $adb -s $Device shell pm grant com.inofarm.jogja.finance android.permission.ACCESS_LOCAL_NETWORK}
    & $php (Join-Path $PSScriptRoot 'finance-report-export-fixture.php') create
    if($LASTEXITCODE -ne 0) {throw 'Guarded fixture creation failed.'}
    $fixtureCreated=$true;$fixture=Get-Content $manifest -Raw | ConvertFrom-Json
    if($UseAdbReverse) {
        $existingMaps=(& $adb -s $Device reverse --list) -join "`n"
        if($LASTEXITCODE -ne 0 -or $existingMaps -match '(^|\s)tcp:8001(\s|$)') {throw 'Cannot own Android port8001: an existing reverse mapping must be preserved.'}
        & $adb -s $Device reverse tcp:8001 tcp:8001
        if($LASTEXITCODE -ne 0) {throw 'Owned ADB reverse creation failed.'}
        $reverseCreated=$true;$fixture.base_url='http://127.0.0.1:8001/api/v2/'
        [IO.File]::WriteAllText($manifest,($fixture | ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
    }
    $external='/sdcard/Android/data/com.inofarm.jogja.finance/files'
    & $adb -s $Device shell mkdir -p $external
    & $adb -s $Device push $manifest ($external+'/finance-report-export-fixture.json')
    if($LASTEXITCODE -ne 0) {throw 'Private synthetic manifest push failed.'}
    Start-TestApi
    $probeLogin=Invoke-RestMethod 'http://127.0.0.1:8001/api/v2/auth/login' -Method Post -ContentType 'application/json' -Body (@{email=$fixture.manager_email;password=$fixture.password;device_name='export-wire-probe'} | ConvertTo-Json) -NoProxy
    $probeToken=if($probeLogin.token){$probeLogin.token}else{$probeLogin.data.token}
    if(-not $probeToken){throw 'Probe login did not return a token'}
    $probeHeaders=@{Authorization='Bearer '+$probeToken;Accept='application/pdf'}
    $probePath=Join-Path $outputDirectory 'wire-probe.pdf'
    $probeResponse=Invoke-WebRequest ('http://127.0.0.1:8001/api/v2/report-exports/'+$fixture.season_report_id+'/pdf?expected_snapshot_hash='+$fixture.season_report_hash) -Headers $probeHeaders -OutFile $probePath -PassThru -NoProxy
    $probeExpected=($probeResponse.Headers['X-Content-SHA256'] -join '').ToLowerInvariant();$probeActual=(Get-FileHash -LiteralPath $probePath -Algorithm SHA256).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $outputDirectory 'wire-probe.json'),(@{expected=$probeExpected;actual=$probeActual;bytes=(Get-Item -LiteralPath $probePath).Length;content_type=($probeResponse.Headers['Content-Type'] -join '')} | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    if($probeExpected -ne $probeActual){throw 'Real HTTP wire checksum differs before Android; inspect synthetic wire-probe'}
    foreach($step in @(
        @{Name='export';Method='realNativeExportsRemainBoundAfterSourceCorrections'}
    )) {
        $phaseLog=Join-Path $outputDirectory ('device-'+$step.Name+'.txt')
        $logProcess=Start-Process -FilePath $adb -ArgumentList @('-s',$Device,'logcat','-b','main','-b','system','-b','crash','-b','events','-v','threadtime','-T','1') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $outputDirectory ('logcat-'+$step.Name+'.txt')) -RedirectStandardError (Join-Path $outputDirectory ('logcat-'+$step.Name+'.stderr.txt'))
        $phaseProcess=Start-Process -FilePath $adb -ArgumentList @('-s',$Device,'shell','am','instrument','-w','-r','-e','class',('com.inofarm.jogja.FinanceReportExportBackendDeviceTest#'+$step.Method),'-e','devicePhase',$step.Name,'-e','restartMode',$(if($RebootBetweenPhases) {'os-reboot'} else {'process'}),'com.inofarm.jogja.finance.test/androidx.test.runner.AndroidJUnitRunner') -WindowStyle Hidden -PassThru -RedirectStandardOutput $phaseLog -RedirectStandardError (Join-Path $outputDirectory ('device-'+$step.Name+'.stderr.txt'))
        Assert-Instrumentation $phaseProcess $phaseLog
        Stop-TestLog
    }
    $proofPath=Join-Path $outputDirectory 'proof.json'
    & $adb -s $Device pull ($external+'/finance-report-export-proof.json') $proofPath
    if($LASTEXITCODE -ne 0 -or (Get-Content $proofPath -Raw | ConvertFrom-Json).fixture_id -ne $fixture.fixture_id) {throw 'Native export proof does not match this owned fixture.'}
    & $php (Join-Path $PSScriptRoot 'finance-report-export-fixture.php') verify
    if($LASTEXITCODE -ne 0) {throw 'Report SQL reconciliation failed.'}
} finally {
    Stop-TestLog
    Stop-TestApi
    if($phaseProcess -and -not $phaseProcess.HasExited) {
        $phaseOwner=Get-CimInstance Win32_Process -Filter "ProcessId=$($phaseProcess.Id)"
        if($phaseOwner.Name -ne 'adb.exe' -or -not $phaseOwner.CommandLine.Contains('com.inofarm.jogja.FinanceReportExportBackendDeviceTest#') -or -not $phaseOwner.CommandLine.Contains($Device)) {throw 'Instrumentation ownership changed: keep fixture for review.'}
        & $adb -s $Device shell am force-stop com.inofarm.jogja.finance
        if(-not $phaseProcess.WaitForExit(5000)) {Stop-Process -Id $phaseProcess.Id}
    }
    if($fixtureCreated) {& $php (Join-Path $PSScriptRoot 'finance-report-export-fixture.php') cleanup;if($LASTEXITCODE -ne 0) {throw 'Synthetic fixture cleanup failed; inspect private manifest before rerunning.'}}
    if($reverseCreated) {
        $maps=(& $adb -s $Device reverse --list) -join "`n"
        if($maps -notmatch 'tcp:8001\s+tcp:8001') {throw 'Owned ADB reverse changed; refusing to remove another mapping.'}
        & $adb -s $Device reverse --remove tcp:8001
        if($LASTEXITCODE -ne 0) {throw 'Owned ADB reverse cleanup failed.'}
    }
    $env:GRADLE_USER_HOME=$oldGradleHome;$env:APP_ENV=$oldAppEnv
}
