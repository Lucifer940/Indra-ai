@echo off
REM INDRA AI - one command: backend in its own window, then the desktop app
setlocal
cd /d "%~dp0.."
start "INDRA AI backend" cmd /k "%~dp0run-backend.bat"
timeout /t 4 /nobreak >nul
call "%~dp0run-desktop.bat"
endlocal
