-- Add applying_for_tier column to users (optional; JPA ddl-auto may add it).
-- Run if you manage schema manually: psql -U postgres -d biasharahub -f add-applying-for-tier.sql

ALTER TABLE users
ADD COLUMN IF NOT EXISTS applying_for_tier VARCHAR(32);

COMMENT ON COLUMN users.applying_for_tier IS 'Tier the owner is applying for (tier1/tier2/tier3). Set at onboarding.';
