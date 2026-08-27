@echo off
setlocal
chcp 65001 >nul
cd /d "%~dp0"
set "ASBP_SCRIPT=%~dp0scripts\start-server.ps1"
set "ASBP_BOOTSTRAP=%~dp0scripts\invoke-start-server.ps1"
set "ASBP_POWERSHELL=%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe"
set "ASBP_ARGUMENTS="
if "%ASBP_LAUNCHER_BUILD_ONLY%"=="1" set "ASBP_ARGUMENTS=-BuildOnly"

echo [ASBP] Starting launcher...
if not exist "%ASBP_SCRIPT%" goto missing_script
if not exist "%ASBP_BOOTSTRAP%" goto missing_script
if not exist "%ASBP_POWERSHELL%" goto missing_powershell

"%ASBP_POWERSHELL%" -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%ASBP_BOOTSTRAP%" %ASBP_ARGUMENTS%
set "ASBP_EXIT=%ERRORLEVEL%"
if not "%ASBP_EXIT%"=="0" goto failure
endlocal
exit /b 0

:missing_script
echo [ASBP] ERROR: Launcher scripts are missing.
goto failure

:missing_powershell
echo [ASBP] ERROR: Windows PowerShell was not found.
goto failure

:failure
echo.
echo [ASBP] Launch failed. See the Japanese message above and .runtime\logs\launcher-latest.log.
pause
endlocal
exit /b 1
