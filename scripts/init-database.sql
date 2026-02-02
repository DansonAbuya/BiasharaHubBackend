-- BiasharaHub: Initialize database if not exists
-- Run this against the 'postgres' database (default PostgreSQL database)
-- Usage: psql -U postgres -h localhost -f init-database.sql

SELECT 'CREATE DATABASE biasharahub'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'biasharahub')\gexec
