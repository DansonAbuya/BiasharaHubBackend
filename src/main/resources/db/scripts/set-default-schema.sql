-- One-time setup: set default schema so the app does not need currentSchema in the JDBC URL.
-- Run as a superuser or role with ALTER DATABASE / ALTER ROLE privilege.
--
-- Replace YOUR_DATABASE with the database name from your DB_URL (e.g. biasharahub_test, biasharahub).
--
-- Option A: For the whole database (all roles connecting to it)
-- ALTER DATABASE YOUR_DATABASE SET search_path TO public;

-- Option B: For the application role only (replace 'postgres' with your DB_USERNAME if different)
-- ALTER ROLE postgres SET search_path TO public;

-- Example for biasharahub_test and biasharahub:
-- ALTER DATABASE biasharahub_test SET search_path TO public;
-- ALTER DATABASE biasharahub SET search_path TO public;

-- Existing connections keep their current search_path; new connections pick up the default.
-- Verify (after reconnecting): SHOW search_path;
