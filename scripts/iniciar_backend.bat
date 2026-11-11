@echo off
chcp 65001 >nul
setlocal

rem --- buscar un JDK 17 o 21 (Spring Boot 3.3 NO funciona bien con Java 25 o 27) ---
set "JH="
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do if not defined JH set "JH=%%~fD"
for /d %%D in ("C:\Program Files\Java\jdk-17*") do if not defined JH set "JH=%%~fD"
for /d %%D in ("C:\Program Files\Microsoft\jdk-17*") do if not defined JH set "JH=%%~fD"
for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-21*") do if not defined JH set "JH=%%~fD"
for /d %%D in ("C:\Program Files\Java\jdk-21*") do if not defined JH set "JH=%%~fD"
for /d %%D in ("C:\Program Files\Microsoft\jdk-21*") do if not defined JH set "JH=%%~fD"

if not defined JH (
  echo No encontre JDK 17 ni 21 automaticamente.
  echo Escribe la ruta completa de tu JDK 17, por ejemplo:
  echo C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
  set /p JH=Ruta: 
)
if not exist "%JH%\bin\java.exe" (
  echo *** Esa ruta no tiene bin\java.exe. Instala Temurin 17 desde https://adoptium.net ***
  goto fin
)

set "JAVA_HOME=%JH%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo Usando Java en: %JAVA_HOME%

set PORT=5433
set /p PORT=Puerto de PostgreSQL (Enter = 5433): 
if "%PORT%"=="" set PORT=5433
set DB_URL=jdbc:postgresql://localhost:%PORT%/cimse

cd /d "%~dp0..\backend"
echo.
echo Iniciando backend en http://localhost:8080 con la BD en el puerto %PORT%
echo La primera vez descarga Maven y las librerias: puede tardar varios minutos.
echo NO hagas clic dentro de esta ventana, porque se pausa. Espera el mensaje Started CimseApplication
echo.
set MAVEN_USER_HOME=C:\m2
call mvnw.cmd spring-boot:run

:fin
echo.
pause
