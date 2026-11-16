@echo off
REM INDRA AI - launch the desktop app on Windows
setlocal
cd /d "%~dp0..\modules\indra-desktop"
call gradle run --no-daemon
endlocal
