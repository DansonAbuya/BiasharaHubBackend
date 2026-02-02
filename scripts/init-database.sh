#!/bin/bash
# BiasharaHub: Create database if it doesn't exist
# Usage: ./init-database.sh

DB_USER="${DB_USERNAME:-postgres}"
DB_HOST="${DB_HOST:-localhost}"
DB_PASSWORD="${DB_PASSWORD:-postgres}"
export PGPASSWORD="$DB_PASSWORD"

echo "Checking if biasharahub database exists..."
EXISTS=$(psql -U "$DB_USER" -h "$DB_HOST" -tAc "SELECT 1 FROM pg_database WHERE datname='biasharahub'")

if [ -z "$EXISTS" ]; then
    echo "Database biasharahub not found. Creating..."
    psql -U "$DB_USER" -h "$DB_HOST" -c "CREATE DATABASE biasharahub"
    if [ $? -eq 0 ]; then
        echo "Database biasharahub created successfully."
    else
        echo "Failed to create database."
        exit 1
    fi
else
    echo "Database biasharahub already exists."
fi

exit 0
