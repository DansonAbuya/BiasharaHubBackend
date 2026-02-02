@echo off
REM BiasharaHub: Create database if it doesn't exist
REM Requires PostgreSQL client (psql) in PATH
REM
REM Usage: init-database.bat
REM   Or:  set DB_USERNAME=myuser && set DB_PASSWORD=mypass && init-database.bat
REM
REM Environment variables (optional):
REM   DB_USERNAME  - PostgreSQL user (default: postgres)
REM   DB_PASSWORD  - PostgreSQL password (default: postgres)
REM   DB_HOST      - PostgreSQL host (default: localhost)

setlocal
set "DB_USER=%DB_USERNAME%"
if "%DB_USER%"=="" set "DB_USER=postgres"

set "DB_HOST=%DB_HOST%"
if "%DB_HOST%"=="" set "DB_HOST=localhost"

if defined DB_PASSWORD (set "PGPASSWORD=%DB_PASSWORD%") else (set "PGPASSWORD=postgres")

echo [BiasharaHub] Checking if database 'biasharahub' exists...
for /f "delims=" %%i in ('psql -U %DB_USER% -h %DB_HOST% -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='biasharahub'" 2^>nul') do set "EXISTS=%%i"

if "%EXISTS%"=="1" (
    echo [BiasharaHub] Database 'biasharahub' already exists.
    exit /b 0
)

echo [BiasharaHub] Database not found. Creating 'biasharahub'...
psql -U %DB_USER% -h %DB_HOST% -d postgres -c "CREATE DATABASE biasharahub"
if errorlevel 1 (
    echo [BiasharaHub] ERROR: Failed to create database. Ensure PostgreSQL is running and credentials are correct.
    exit /b 1
)
echo [BiasharaHub] Database 'biasharahub' created successfully.
exit /b 0
