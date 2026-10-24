param([switch]$Test,[switch]$HardwareCompileCheck)
$projectRoot=Split-Path $PSScriptRoot -Parent
$env:PLATFORMIO_CORE_DIR=Join-Path $projectRoot '.tools/platformio'
$pythonExe=Join-Path $projectRoot '.tools/pio-venv/Scripts/python.exe'
if (-not (Test-Path $pythonExe)) { throw 'Install PlatformIO virtualenv first; see README' }
& $pythonExe (Join-Path $PSScriptRoot 'platformio-path-fix.py')
if ($LASTEXITCODE -ne 0) { throw 'PlatformIO path compatibility check failed' }
if ($Test) {
    $compilerBin=Join-Path $env:PLATFORMIO_CORE_DIR 'packages/toolchain-gccmingw32/bin'
    if (Test-Path $compilerBin) { $env:Path="$compilerBin;$env:Path" }
    & $pythonExe -m platformio test -d (Join-Path $projectRoot 'firmware') -e native
}
else {
    # GCC 8 expands ESP32 include flags beyond Windows' 32767 character limit
    # in this long workspace path. SUBST shortens paths without moving any files.
    $buildDrive=@('Z:','Y:','X:') | Where-Object { -not (Test-Path "$_\") } | Select-Object -First 1
    if (-not $buildDrive) { throw 'No free temporary drive letter for ESP32 build' }
    & subst.exe $buildDrive $projectRoot
    if ($LASTEXITCODE -ne 0) { throw 'Cannot map workspace for short compiler paths' }
    try {
        $env:PLATFORMIO_CORE_DIR="$buildDrive\.tools\platformio"
        $firmwareEnvironment=if ($HardwareCompileCheck) { 'esp32_compile_check' } else { 'esp32dev' }
        & "$buildDrive\.tools\pio-venv\Scripts\python.exe" -m platformio run -d "$buildDrive\firmware" -e $firmwareEnvironment
        $firmwareResult=$LASTEXITCODE
        if ($firmwareResult -eq 0 -and -not $HardwareCompileCheck) {
            # Switching between mapped ESP32 and native paths invalidates PIO's
            # generated build cache. Preserve outputs before the next environment.
            $firmwareOutput=Join-Path $projectRoot 'output'
            New-Item -ItemType Directory -Force $firmwareOutput | Out-Null
            foreach ($artifact in @('firmware.bin','firmware.elf','bootloader.bin','partitions.bin')) {
                $outputName=$artifact.Replace('.', '-headless.')
                Copy-Item -LiteralPath "$buildDrive\firmware\.pio\build\esp32dev\$artifact" -Destination (Join-Path $firmwareOutput $outputName)
            }
        }
    } finally {
        & subst.exe $buildDrive /D
        $env:PLATFORMIO_CORE_DIR=Join-Path $projectRoot '.tools/platformio'
    }
    if ($firmwareResult -ne 0) { throw 'Firmware build failed' }
}
if ($LASTEXITCODE -ne 0) { throw 'Firmware build/test failed' }
