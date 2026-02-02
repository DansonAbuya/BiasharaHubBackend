# BiasharaHub: Create database if it doesn't exist
# Requires PostgreSQL client (psql) in PATH
#
# Usage: .\init-database.ps1
#   Or:  $env:DB_USERNAME="myuser"; $env:DB_PASSWORD="mypass"; .\init-database.ps1
#
# Environment variables (optional):
#   DB_USERNAME  - PostgreSQL user (default: postgres)
#   DB_PASSWORD  - PostgreSQL password (default: postgres)
#   DB_HOST      - PostgreSQL host (default: localhost)

$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "postgres" }
$dbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$dbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "postgres" }

$env:PGPASSWORD = $dbPassword

Write-Host "[BiasharaHub] Checking if database 'biasharahub' exists..." -ForegroundColor Cyan
$exists = psql -U $dbUser -h $dbHost -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='biasharahub'" 2>$null

if ($exists -match "1") {
    Write-Host "[BiasharaHub] Database 'biasharahub' already exists." -ForegroundColor Green
    exit 0
}

Write-Host "[BiasharaHub] Database not found. Creating 'biasharahub'..." -ForegroundColor Yellow
$result = psql -U $dbUser -h $dbHost -d postgres -c "CREATE DATABASE biasharahub" 2>&1

if ($LASTEXITCODE -ne 0) {
    Write-Host "[BiasharaHub] ERROR: Failed to create database. Ensure PostgreSQL is running and credentials are correct." -ForegroundColor Red
    Write-Host $result
    exit 1
}

Write-Host "[BiasharaHub] Database 'biasharahub' created successfully." -ForegroundColor Green
exit 0
