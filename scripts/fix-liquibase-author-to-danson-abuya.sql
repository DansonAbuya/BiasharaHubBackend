-- Run this ONCE after changing Liquibase changeset author from "cursor-ai" to "Danson Abuya".
-- It updates the recorded author in databasechangelog so Liquibase does not re-apply those changesets.
-- Use the same database as the app (see application.yml). Example: psql -U postgres -d biasharahub -f scripts/fix-liquibase-author-to-danson-abuya.sql

UPDATE databasechangelog
SET author = 'Danson Abuya'
WHERE author = 'cursor-ai';
