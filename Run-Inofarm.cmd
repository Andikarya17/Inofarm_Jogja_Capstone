@echo off
set "INOFARM_PWSH=pwsh.exe"
where pwsh.exe >nul 2>nul
if errorlevel 1 set "INOFARM_PWSH=%ProgramFiles%\PowerShell\7\pwsh.exe"
if not exist "%INOFARM_PWSH%" (
  if "%INOFARM_PWSH%" NEQ "pwsh.exe" set "INOFARM_PWSH=%USERPROFILE%\.cache\codex-runtimes\codex-primary-runtime\dependencies\native\powershell\pwsh.exe"
)
"%INOFARM_PWSH%" -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\run-android.ps1" %*
if errorlevel 1 (
  echo Launcher gagal. Lihat pesan di atas.
  pause
  exit /b 1
)
echo Selesai. Aplikasi sudah dibuka pada perangkat.
pause
