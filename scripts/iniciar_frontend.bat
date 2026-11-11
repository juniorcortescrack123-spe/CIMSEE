@echo off
chcp 65001 >nul
cd /d "%~dp0..\frontend"
if not exist node_modules call npm install
echo Iniciando frontend en http://localhost:5173 ...
call npm run dev
pause
