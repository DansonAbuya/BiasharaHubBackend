-- Trust & Safety: seller strikes, account status, verification checklist (per tenant)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS strike_count INTEGER NOT NULL DEFAULT 0', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS account_status VARCHAR(32) DEFAULT ''active''', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS banned_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS phone_verified_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS mpesa_validated_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS business_location_verified_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMP WITH TIME ZONE', r.schema_name);
    END LOOP;
END;
$$;
