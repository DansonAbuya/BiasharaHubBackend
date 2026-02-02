-- Run this once if Liquibase fails with "checksum" validation for 002-create-tenant-schema-function.
-- Connects to the same database as the app (see application.yml for URL).
-- PostgreSQL: DATABASECHANGELOG is lowercase by default; use databasechangelog if needed.

UPDATE databasechangelog
SET md5sum = '9:cc59c9fa372880f8dd1ddce106fa0377'
WHERE id = '002-create-tenant-schema-function'
  AND author = 'biasharahub';
