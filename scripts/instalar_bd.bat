@echo off
chcp 65001 >nul
setlocal
set PGCLIENTENCODING=UTF8

echo ============================================================
echo  CIMSE - Instalar (o reiniciar) la base de datos local
echo  ATENCION: si la base "cimse" ya existe, SE BORRA y se recrea.
echo ============================================================
echo.

rem --- buscar psql en las rutas por defecto de PostgreSQL ---
set "PSQL="
for %%V in (18 17 16 15 14 13 12) do (
  if not defined PSQL if exist "C:\Program Files\PostgreSQL\%%V\bin\psql.exe" set "PSQL=C:\Program Files\PostgreSQL\%%V\bin\psql.exe"
)
if not defined PSQL (
  echo No encontre psql.exe en C:\Program Files\PostgreSQL\
  echo Instala PostgreSQL o escribe la ruta completa de psql.exe:
  set /p PSQL=Ruta:
)

set PORT=5433
set /p PORT=Puerto de PostgreSQL (Enter = 5433): 
if "%PORT%"=="" set PORT=5433

set /p PGPASSWORD=Contrasena del usuario postgres: 

set "SCHEMA=%~dp0..\database\schema.sql"
set "SEED=%~dp0..\database\seed.sql"

echo.
echo [1/5] Creando usuario cimse_user...
"%PSQL%" -h localhost -p %PORT% -U postgres -d postgres -c "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname='cimse_user') THEN CREATE ROLE cimse_user LOGIN PASSWORD 'cimse_pass'; END IF; END $$;"
if errorlevel 1 goto error

echo [2/5] Borrando base cimse anterior (si existe)...
"%PSQL%" -h localhost -p %PORT% -U postgres -d postgres -c "DROP DATABASE IF EXISTS cimse WITH (FORCE);"
if errorlevel 1 goto error

echo [3/5] Creando base cimse...
"%PSQL%" -h localhost -p %PORT% -U postgres -d postgres -c "CREATE DATABASE cimse OWNER cimse_user;"
if errorlevel 1 goto error

echo [4/5] Cargando tablas y datos de prueba...
"%PSQL%" -h localhost -p %PORT% -U postgres -d cimse -v ON_ERROR_STOP=1 -q -f "%SCHEMA%"
if errorlevel 1 goto error
"%PSQL%" -h localhost -p %PORT% -U postgres -d cimse -v ON_ERROR_STOP=1 -q -f "%SEED%"
if errorlevel 1 goto error

echo [5/5] Dando permisos a cimse_user...
"%PSQL%" -h localhost -p %PORT% -U postgres -d cimse -q -c "GRANT ALL ON ALL TABLES IN SCHEMA public TO cimse_user; GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO cimse_user;"
if errorlevel 1 goto error

echo.
echo Tablas creadas (debe decir 34):
"%PSQL%" -h localhost -p %PORT% -U postgres -d cimse -t -A -c "SELECT count(*) FROM information_schema.tables WHERE table_schema='public';"
echo.
echo LISTO. Base "cimse" instalada en el puerto %PORT%.
goto fin

:error
echo.
echo *** ERROR. Revisa el mensaje de arriba (contrasena, puerto o servicio de PostgreSQL apagado). ***

:fin
echo.
pause
