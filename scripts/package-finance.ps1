#requires -Version 7.0
param([string]$JavaHome='',[string]$SdkPath="$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference='Stop'
$project=Split-Path $PSScriptRoot -Parent
if(& git -C $project status --porcelain --untracked-files=no){throw 'Commit tracked changes before packaging so APK provenance is reviewable.'}
$commit=(& git -C $project rev-parse HEAD).Trim()
& (Join-Path $PSScriptRoot 'build-android.ps1') -JavaHome $JavaHome -SdkPath $SdkPath -Tasks @('-PfinanceApp',':app:assembleDebug')
if($LASTEXITCODE -ne 0){throw 'Finance build failed.'}
if((& git -C $project rev-parse HEAD).Trim() -ne $commit -or (& git -C $project status --porcelain --untracked-files=no)){throw 'Source changed during packaging; no package published.'}
$apk=Join-Path $project 'android/app/build/outputs/apk/debug/app-debug.apk'
$metadata=Get-Content -LiteralPath (Join-Path (Split-Path $apk) 'output-metadata.json') -Raw | ConvertFrom-Json
if($metadata.applicationId -ne 'com.inofarm.jogja.finance' -or $metadata.variantName -ne 'debug'){throw 'Only the separate finance debug APK may be packaged here.'}
$aapt=Get-ChildItem -Path (Join-Path $SdkPath 'build-tools/*/aapt2.exe') -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if(-not $aapt){throw 'Android SDK aapt2 is required for APK identity verification.'}
$badging=(& $aapt.FullName dump badging $apk) -join "`n"
if($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='com.inofarm.jogja.finance'" -or $badging -notmatch "(?:minSdkVersion|sdkVersion):'29'" -or $badging -notmatch 'application-debuggable'){throw 'APK identity/minSdk/debug verification failed.'}
$name='finance-review-'+$commit.Substring(0,8)+'-'+(Get-Date -Format 'yyyyMMdd-HHmmss')
$directory=Join-Path $project ('output/finance/'+$name)
if(Test-Path -LiteralPath $directory){throw 'Package already exists; preserving it.'}
New-Item -ItemType Directory -Path $directory | Out-Null
$target=Join-Path $directory 'inofarm-finance-review.apk'
Copy-Item -LiteralPath $apk -Destination $target
$hash=(Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
$manifest=[ordered]@{kind='internal-debug-review';source_commit=$commit;application_id=$metadata.applicationId;min_sdk=29;version_name=$metadata.elements[0].versionName;apk='inofarm-finance-review.apk';apk_sha256=$hash;composer_lock_sha256=(Get-FileHash -LiteralPath (Join-Path $project 'backend/composer.lock') -Algorithm SHA256).Hash.ToLowerInvariant();acceptance='See FIN16_ACCEPTANCE and FIN17_CUTOVER; UAT/pentest/production approval remain separate.'}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $directory 'manifest.json') -Encoding utf8
@"
INOFARM - internal Android finance review
Commit: $commit
APK SHA256: $hash
Install/update only the separate com.inofarm.jogja.finance test app with adb install -r.
Do not clear app data. Configure your own API URL/account. No server/database/credential is bundled.
Guide: https://github.com/Andikarya17/Inofarm_Jogja_Capstone/blob/codex/finance-cutover-docs/docs/migration/finance-20261029/FIN17_INSTALL_RUN.md
Gates: https://github.com/Andikarya17/Inofarm_Jogja_Capstone/blob/codex/finance-cutover-docs/docs/migration/finance-20261029/FIN17_CUTOVER.md
This debug package is not a production deployment or acceptance sign-off.
"@ | Set-Content -LiteralPath (Join-Path $directory 'INSTALL.txt') -Encoding utf8
Get-ChildItem -LiteralPath $directory -File | Sort-Object Name | ForEach-Object {"$((Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)"} | Set-Content -LiteralPath (Join-Path $directory 'SHA256SUMS.txt') -Encoding utf8
$archive=$directory+'.zip'
Compress-Archive -LiteralPath $target,(Join-Path $directory 'manifest.json'),(Join-Path $directory 'INSTALL.txt'),(Join-Path $directory 'SHA256SUMS.txt') -DestinationPath $archive
Write-Output "Finance review package: $archive"
Write-Output "APK SHA256: $hash"
