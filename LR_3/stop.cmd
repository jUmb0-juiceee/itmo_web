@echo off
cd /d "%~dp0"
python -X utf8 scripts\project.py stop
if errorlevel 1 pause
