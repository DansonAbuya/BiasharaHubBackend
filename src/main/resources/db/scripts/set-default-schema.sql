-- One-time setup: set default schema for BiasharaHub so the app does not need
-- currentSchema in the JDBC URL (better security: server enforces schema).
-- Run as a superuser or role with ALTER DATABASE / ALTER ROLE privilege.
--
-- Use ONE of the following:
--
-- Option A: For the whole database (all roles connecting to it)
ALTER DATABASE biasharahub SET search_path TO public;

-- Option B: For the application role only (replace 'postgres' with your DB_USERNAME if different)
-- ALTER ROLE postgres SET search_path TO public;

-- Existing connections keep their current search_path; new connections pick up the default.
-- Verify (after reconnecting): SHOW search_path;
