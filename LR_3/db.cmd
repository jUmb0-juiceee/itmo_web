@echo off
cd /d "%~dp0"
python -X utf8 scripts\database.py %*
if errorlevel 1 pause
