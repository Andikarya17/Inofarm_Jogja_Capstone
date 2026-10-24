$ErrorActionPreference='Stop'
$projectRoot=Split-Path $PSScriptRoot -Parent
$outputDir=Join-Path $projectRoot 'output'
New-Item -ItemType Directory -Force $outputDir | Out-Null
$artifacts=@{
    'android/app/build/outputs/apk/debug/app-debug.apk'='inofarm-jogja-debug.apk'
    'android/app/build/reports/lint-results-debug.txt'='android-lint.txt'
}
foreach ($firmwareFile in @('firmware-headless.bin','firmware-headless.elf','bootloader-headless.bin','partitions-headless.bin')) {
    if (-not (Test-Path (Join-Path $outputDir $firmwareFile))) { throw "Run build-firmware.ps1 first: $firmwareFile missing" }
}
foreach ($entry in $artifacts.GetEnumerator()) {
    $source=Join-Path $projectRoot $entry.Key
    if (-not (Test-Path $source)) { throw "Build artifact missing: $($entry.Key)" }
    Copy-Item -LiteralPath $source -Destination (Join-Path $outputDir $entry.Value)
}
Get-ChildItem (Join-Path $projectRoot 'android/app/build/test-results/testDebugUnitTest') -Filter '*.xml' | Copy-Item -Destination $outputDir
Get-ChildItem (Join-Path $projectRoot 'android/app/build/outputs/androidTest-results/connected/debug') -Filter '*.xml' | Copy-Item -Destination $outputDir
Get-ChildItem $outputDir -File | Where-Object Name -ne 'SHA256SUMS.txt' | ForEach-Object {
    "$( (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant())  $($_.Name)"
} | Set-Content (Join-Path $outputDir 'SHA256SUMS.txt')
Write-Output 'Local artifacts packaged in output. Firmware is headless; no flashing performed.'
