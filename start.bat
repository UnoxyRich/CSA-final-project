@echo off
setlocal

cd /d "%~dp0"

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-windows.ps1" %*
set "LAUNCH_EXIT=%ERRORLEVEL%"

if not "%LAUNCH_EXIT%"=="0" (
  echo.
  echo Launcher failed with exit code %LAUNCH_EXIT%.
  pause
)

exit /b %LAUNCH_EXIT%
