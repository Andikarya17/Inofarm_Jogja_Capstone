#requires -Version 7.0
param([Parameter(Mandatory=$true)][string]$JavaHome,[string]$SdkPath="$env:LOCALAPPDATA/Android/Sdk",[Parameter(Mandatory=$true)][string]$PhpPath,[string]$GradleHome,[string]$MySqlBin='C:/laragon/bin/mysql/mysql-8.0.30-winx64/bin',[string]$Device='emulator-5558',[switch]$SkipBuild,[switch]$SkipDevice,[switch]$RebootBetweenPhases)
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent;$adb=Join-Path $SdkPath 'platform-tools/adb.exe';$fixture=Join-Path $project 'tmp/finance-recovery-fixture.json';$before=Join-Path $project 'tmp/finance-recovery-device-before.json'
foreach($file in @($fixture,$before,(Join-Path $project 'tmp/finance-report-export-fixture.json'),(Join-Path $project 'tmp/finance-season-report-fixture.json'))) {if(Test-Path -LiteralPath $file){throw 'Existing private fixture/proof preserved; inspect ownership before another drill.'}}
function Assert-Free([int]$Port) {$client=[Net.Sockets.TcpClient]::new();try {try {$task=$client.ConnectAsync('127.0.0.1',$Port);$occupied=$task.Wait(500) -and $client.Connected}catch{$occupied=$false};if($occupied){throw "Port$Port is occupied; existing service preserved."}}finally{$client.Dispose()}}
Assert-Free 8001;Assert-Free 3309
$nonce=[guid]::NewGuid().ToString();$directory=Join-Path $project ('.runtime/recovery-drills/'+$nonce);$out=Join-Path $directory 'evidence';New-Item -ItemType Directory $out | Out-Null
$mysqlData=Join-Path $directory 'mysql-data';$files=Join-Path $directory 'files';$sourceFiles=Join-Path $directory 'source-files';$packages=Join-Path $directory 'packages'
foreach($path in @($mysqlData,$files,$sourceFiles,$packages)){New-Item -ItemType Directory $path | Out-Null}
$saved=@{};foreach($key in @('APP_ENV','DB_HOST','DB_PORT','DB_DATABASE','DB_USERNAME','DB_PASSWORD','DB_URL','FINANCE_PRIVATE_ROOT','FINANCE_RECOVERY_ROOT','FINANCE_RESTORE_ISOLATION_MARKER','FINANCE_RECOVERY_DRILL_ID','FINANCE_RECOVERY_OPERATORS','GRADLE_USER_HOME')){$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$api=$null;$mysqlProcess=$null;$instrument=$null;$reverse=$false;$created=$false;$f=$null
$router=Join-Path $directory 'router.php';$laravelRouter=(Join-Path $project 'backend/vendor/laravel/framework/src/Illuminate/Foundation/resources/server.php').Replace('\','/')
$routerText='<?php if(parse_url($_SERVER["REQUEST_URI"],PHP_URL_PATH)==="/__recovery_probe/NONCE"){header("Content-Type: application/json");echo json_encode(["nonce"=>"NONCE","pid"=>getmypid()]);return;} require "ROUTER";'
[IO.File]::WriteAllText($router,$routerText.Replace('NONCE',$nonce).Replace('ROUTER',$laravelRouter),[Text.UTF8Encoding]::new($false))
function Check-Code([string]$Message){if($LASTEXITCODE -ne 0){throw $Message}}
function Source-Environment {
    $env:APP_ENV='testing';$env:DB_HOST='127.0.0.1';$env:DB_PORT='3308';$env:DB_DATABASE='inofarm_test';$env:DB_URL=''
    foreach($key in @('DB_USERNAME','DB_PASSWORD')) {if($null -eq $saved[$key]) {Remove-Item -LiteralPath ('Env:'+$key) -ErrorAction SilentlyContinue} else {[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}}
    $env:FINANCE_PRIVATE_ROOT=$sourceFiles;$env:FINANCE_RECOVERY_ROOT=$packages;$env:FINANCE_RECOVERY_DRILL_ID=$nonce
}
function Stop-Api {
    if($script:api -and -not $script:api.HasExited){$p=Get-CimInstance Win32_Process -Filter "ProcessId=$($script:api.Id)";if($p.Name -ne 'php.exe' -or -not $p.CommandLine.Contains($router)){throw 'PHP ownership changed; refusing to stop it.'};Stop-Process -Id $p.ProcessId;$script:api.WaitForExit(5000)|Out-Null};$script:api=$null
}
function Start-Api([string]$Phase) {
    Assert-Free 8001
    $script:api=Start-Process $PhpPath -WorkingDirectory (Join-Path $project 'backend/public') -ArgumentList @('-S','127.0.0.1:8001','-t',(Join-Path $project 'backend/public'),$router) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $out ($Phase+'-server.stdout.txt')) -RedirectStandardError (Join-Path $out ($Phase+'-server.stderr.txt'))
    $deadline=[DateTime]::UtcNow.AddSeconds(30)
    do {if($script:api.HasExited){throw 'Owned test PHP exited'};try {$probe=Invoke-RestMethod ('http://127.0.0.1:8001/__recovery_probe/'+$nonce) -NoProxy -TimeoutSec 2;if($probe.nonce -eq $nonce -and $probe.pid -eq $script:api.Id){return}}catch{};Start-Sleep -Milliseconds 200}while([DateTime]::UtcNow -lt $deadline)
    throw 'Owned recovery API readiness failed'
}
function Device-Phase([string]$Phase,[string]$Method,[string]$ClassName='FinanceRecoveryBackendDeviceTest') {
    $log=Join-Path $out ('device-'+$Phase+'.txt')
    $script:instrument=Start-Process $adb -ArgumentList @('-s',$Device,'shell','am','instrument','-w','-r','-e','class',('com.inofarm.jogja.'+$ClassName+'#'+$Method),'-e','devicePhase',$Phase,'com.inofarm.jogja.finance.test/androidx.test.runner.AndroidJUnitRunner') -WindowStyle Hidden -PassThru -RedirectStandardOutput $log -RedirectStandardError ($log+'.stderr')
    $deadline=[DateTime]::UtcNow.AddSeconds(180);while(-not $script:instrument.WaitForExit(2000)){if([DateTime]::UtcNow -gt $deadline){throw 'Recovery device phase timed out'}}
    if((Get-Content $log -Raw) -notmatch 'OK \(1 test\)'){throw "Device phase $Phase failed; inspect owned evidence."}
}
try {
    Source-Environment;if($GradleHome){$env:GRADLE_USER_HOME=$GradleHome}
    if(-not $SkipDevice -and -not $SkipBuild){& (Join-Path $JavaHome 'bin/java.exe') --enable-native-access=ALL-UNNAMED -classpath android/gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -p android -PfinanceApp :app:assembleDebug :app:assembleDebugAndroidTest --console=plain --max-workers=2 --no-daemon '-Pkotlin.compiler.execution.strategy=in-process';Check-Code 'Finance APK build failed'}
    & $PhpPath (Join-Path $PSScriptRoot 'finance-recovery-fixture.php') create;Check-Code 'Source fixture creation failed';if(-not(Test-Path -LiteralPath $fixture)){throw 'Source fixture did not create its manifest; inspect diagnostic output'};$created=$true;$f=Get-Content $fixture -Raw|ConvertFrom-Json
    if($SkipDevice){& $PhpPath (Join-Path $PSScriptRoot 'finance-recovery-fixture.php') seed;Check-Code 'Synthetic source operation failed'}else {
        foreach($apk in @('android/app/build/outputs/apk/debug/app-debug.apk','android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')){& $adb -s $Device install -r -t (Join-Path $project $apk);Check-Code 'APK install failed'}
        $maps=(& $adb -s $Device reverse --list)-join "`n";if($LASTEXITCODE -ne 0 -or $maps -match '(^|\s)tcp:8001(\s|$)'){throw 'Existing ADB mapping preserved'}
        & $adb -s $Device reverse tcp:8001 tcp:8001;Check-Code 'ADB reverse failed';$reverse=$true
        & $adb -s $Device shell mkdir -p /sdcard/Android/data/com.inofarm.jogja.finance/files
        & $adb -s $Device push $fixture /sdcard/Android/data/com.inofarm.jogja.finance/files/finance-recovery-fixture.json;Check-Code 'Fixture push failed'
        Start-Api 'before';Device-Phase 'before' 'beforeBackup';Stop-Api
        & $adb -s $Device pull /sdcard/Android/data/com.inofarm.jogja.finance/files/finance-recovery-device-before.json $before;Check-Code 'Device pre-backup proof missing'
    }
    & $PhpPath (Join-Path $PSScriptRoot 'finance-recovery-fixture.php') backup;Check-Code 'Source backup failed';$f=Get-Content $fixture -Raw|ConvertFrom-Json
    if($f.manifest_hash -notmatch '^[0-9a-f]{64}$' -or -not $f.backup_id){throw 'Backup did not produce a complete signed manifest'}
    $serverExe=Join-Path $MySqlBin 'mysqld.exe';$mysqlExe=Join-Path $MySqlBin 'mysql.exe'
    & $serverExe --no-defaults ("--basedir="+(Split-Path $MySqlBin -Parent)) ("--datadir="+$mysqlData) --initialize-insecure --console *> (Join-Path $out 'mysql-initialize.txt');Check-Code 'Owned MySQL initialization failed'
    $mysqlProcess=Start-Process $serverExe -ArgumentList @('--no-defaults',('--basedir="'+(Split-Path $MySqlBin -Parent)+'"'),('--datadir="'+$mysqlData+'"'),'--port=3309','--bind-address=127.0.0.1',('--log-error="'+(Join-Path $out 'mysql.log')+'"')) -WindowStyle Hidden -PassThru
    $deadline=[DateTime]::UtcNow.AddSeconds(60)
    do {if($mysqlProcess.HasExited){throw 'Owned MySQL exited'};try {$socket=[Net.Sockets.TcpClient]::new();$socket.Connect('127.0.0.1',3309);$socket.Dispose();$ready=$true}catch{$ready=$false};if(-not $ready){Start-Sleep -Milliseconds 300}}while(-not $ready -and [DateTime]::UtcNow -lt $deadline)
    if(-not $ready){throw 'Owned MySQL readiness failed'}
    $owner=Get-CimInstance Win32_Process -Filter "ProcessId=$($mysqlProcess.Id)";if($owner.Name -ne 'mysqld.exe' -or -not $owner.CommandLine.Contains($mysqlData)){throw 'MySQL ownership mismatch'}
    $secretBytes=[byte[]]::new(32);[Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes);$rootSecret=[Convert]::ToHexString($secretBytes);[Security.Cryptography.RandomNumberGenerator]::Fill($secretBytes);$targetSecret=[Convert]::ToHexString($secretBytes)
    $sql="CREATE DATABASE inofarm_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootSecret'; CREATE USER 'inofarm_restore_runner'@'127.0.0.1' IDENTIFIED BY '$targetSecret'; GRANT ALL ON inofarm_test.* TO 'inofarm_restore_runner'@'127.0.0.1';"
    $sql | & $mysqlExe --no-defaults --host=127.0.0.1 --port=3309 --user=root;Check-Code 'Owned target bootstrap failed'
    $clientFile=Join-Path $directory 'target-client.cnf';[IO.File]::WriteAllText($clientFile,"[client]`nuser=root`npassword=$rootSecret`nhost=127.0.0.1`nport=3309`n",[Text.UTF8Encoding]::new($false))
    $serverId=(& $mysqlExe ("--defaults-extra-file="+$clientFile) --batch --skip-column-names '--execute=SELECT @@server_uuid').Trim();Check-Code 'Target identity read failed'
    $marker=Join-Path $directory 'owner.json';[IO.File]::WriteAllText($marker,(@{nonce=$nonce;port=3309;server_uuid=$serverId}|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    $env:DB_PORT='3309';$env:DB_USERNAME='inofarm_restore_runner';$env:DB_PASSWORD=$targetSecret;$env:FINANCE_PRIVATE_ROOT=$files;$env:FINANCE_RESTORE_ISOLATION_MARKER=$marker;$env:FINANCE_RECOVERY_OPERATORS=$f.manager_id
    & $PhpPath (Join-Path $project 'backend/artisan') migrate --force --no-interaction *> (Join-Path $out 'target-migrations.txt');Check-Code 'Trusted target migration failed'
    $restoreArgs=@((Join-Path $project 'backend/artisan'),'inofarm:restore-isolated',$f.backup_id,('--manifest-hash='+$f.manifest_hash),('--nonce='+$nonce),'--operator=synthetic-drill-runner','--reason=U19 isolated recovery verification')
    # Corrupt/missing blob must fail before target data import. Only this owned package file is moved.
    $blob=Join-Path $packages ($f.backup_id+'/blobs/'+$f.blob_sha256);if(-not $blob.StartsWith($packages,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe owned blob path'}
    Move-Item -LiteralPath $blob -Destination ($blob+'.held')
    try {& $PhpPath @restoreArgs *> (Join-Path $out 'expected-missing-blob-rejection.txt');if($LASTEXITCODE -eq 0){throw 'Missing file was wrongly restored'}}finally{Move-Item -LiteralPath ($blob+'.held') -Destination $blob}
    & $PhpPath @restoreArgs *> (Join-Path $out 'restore.json');Check-Code 'Restore NOT VERIFIED; inspect retained target'
    $proof=Get-Content (Join-Path $out 'restore.json') -Raw|ConvertFrom-Json
    if($proof.state -ne 'verified_isolated' -or -not $proof.verification.point_in_time_match -or $proof.new_epoch -eq $f.old_epoch){throw 'Restore proof invalid'}
    & $PhpPath @restoreArgs *> (Join-Path $out 'expected-populated-target-rejection.txt');if($LASTEXITCODE -eq 0){throw 'Populated target was overwritten'}
    if(-not $SkipDevice) {
        $f | Add-Member -NotePropertyName restored_run_id -NotePropertyValue $proof.restore_id -Force
        [IO.File]::WriteAllText($fixture,($f|ConvertTo-Json -Depth 30),[Text.UTF8Encoding]::new($false))
        if($RebootBetweenPhases){& $adb -s $Device reboot;Check-Code 'Owned device reboot failed';$deadline=[DateTime]::UtcNow.AddSeconds(180);do {Start-Sleep -Seconds 3;$boot=& $adb -s $Device shell getprop sys.boot_completed 2>$null}while($boot -ne '1' -and [DateTime]::UtcNow -lt $deadline);if($boot -ne '1'){throw 'Device did not reboot'};$reverse=$false;& $adb -s $Device reverse tcp:8001 tcp:8001;Check-Code 'Restore ADB reverse failed';$reverse=$true}
        $storageDeadline=[DateTime]::UtcNow.AddSeconds(90)
        do {& $adb -s $Device shell test -d /sdcard/Android 2>$null;$storageReady=$LASTEXITCODE -eq 0;if(-not $storageReady){Start-Sleep -Seconds 2}}while(-not $storageReady -and [DateTime]::UtcNow -lt $storageDeadline)
        if(-not $storageReady){throw 'Device credential storage not ready after reboot; no data cleared'}
        & $adb -s $Device shell mkdir -p /sdcard/Android/data/com.inofarm.jogja.finance/files;Check-Code 'Owned external fixture directory unavailable after reboot'
        & $adb -s $Device push $fixture /sdcard/Android/data/com.inofarm.jogja.finance/files/finance-recovery-fixture.json;Check-Code 'Post-restore fixture push failed'
        Start-Api 'after';Device-Phase 'after' 'afterRestore';Device-Phase 'ui' 'managerSeesVerifiedRestoreFromRoot' 'FinanceRecoveryUiBackendDeviceTest';Stop-Api
        & $adb -s $Device pull /sdcard/Android/data/com.inofarm.jogja.finance/files/finance-recovery-device-after.json (Join-Path $out 'device-proof.json');Check-Code 'Device restore proof missing'
        $deviceProof=Get-Content (Join-Path $out 'device-proof.json') -Raw|ConvertFrom-Json;if($deviceProof.fixture_id -ne $f.fixture_id -or $deviceProof.state -ne 'PASS'){throw 'Device proof does not match this fixture'}
    }
    Source-Environment;& $PhpPath (Join-Path $PSScriptRoot 'finance-recovery-fixture.php') verify-source;Check-Code 'Source database changed'
    Write-Output ("PASS isolated recovery; evidence: "+$out)
} finally {
    Stop-Api
    if($instrument -and -not $instrument.HasExited){$p=Get-CimInstance Win32_Process -Filter "ProcessId=$($instrument.Id)";if($p.Name -ne 'adb.exe' -or $p.CommandLine -notmatch 'FinanceRecovery(?:UiBackend|Backend)DeviceTest#'){throw 'Instrumentation ownership changed; fixture preserved'};& $adb -s $Device shell am force-stop com.inofarm.jogja.finance;$instrument.WaitForExit(5000)|Out-Null}
    if($mysqlProcess) {
        $owned=@(Get-CimInstance Win32_Process -Filter "Name='mysqld.exe'" | Where-Object {$_.CommandLine -and $_.CommandLine.Contains($mysqlData) -and $_.CommandLine.Contains('--port=3309')})
        if($owned.Count -gt 0) {
            if($clientFile -and (Test-Path -LiteralPath $clientFile)) {& (Join-Path $MySqlBin 'mysqladmin.exe') ("--defaults-extra-file="+$clientFile) --connect-timeout=3 shutdown;Check-Code 'Owned MySQL graceful shutdown failed'}
            else {foreach($p in $owned){Stop-Process -Id $p.ProcessId}}
            foreach($p in $owned){$process=Get-Process -Id $p.ProcessId -ErrorAction SilentlyContinue;if($process -and -not $process.WaitForExit(15000)){throw 'Owned MySQL child did not exit; target retained'}}
        }
    }
    if($reverse){$maps=(& $adb -s $Device reverse --list)-join "`n";if($maps -notmatch 'tcp:8001\s+tcp:8001'){throw 'Reverse mapping changed; preserved'};& $adb -s $Device reverse --remove tcp:8001}
    if($created){Source-Environment;& $PhpPath (Join-Path $PSScriptRoot 'finance-recovery-fixture.php') cleanup;Check-Code 'Owned fixture cleanup failed; inspect private manifest'}
    if($created -and (Test-Path -LiteralPath $before)) {$ownedProof=Get-Content $before -Raw|ConvertFrom-Json;if($ownedProof.fixture_id -ne $f.fixture_id){throw 'Private device proof ownership changed; preserved'};Move-Item -LiteralPath $before -Destination (Join-Path $out 'private-device-before.json')}
    foreach($key in $saved.Keys){if($null -eq $saved[$key]){Remove-Item -LiteralPath ('Env:'+$key) -ErrorAction SilentlyContinue}else{[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}}
}
