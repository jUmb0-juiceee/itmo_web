@echo off
cd /d "%~dp0"
python -X utf8 testing\run.py %*
set "test_exit=%errorlevel%"
if not "%test_exit%"=="0" pause
exit /b %test_exit%
