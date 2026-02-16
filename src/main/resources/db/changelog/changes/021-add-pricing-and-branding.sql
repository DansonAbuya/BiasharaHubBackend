-- Add pricing_plan and white-label branding columns to users in existing tenant schemas
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS pricing_plan VARCHAR(50)', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS branding_enabled BOOLEAN DEFAULT false', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS branding_name VARCHAR(255)', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS branding_logo_url TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS branding_primary_color VARCHAR(32)', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS branding_secondary_color VARCHAR(32)', r.schema_name);
    END LOOP;
END;
$$;

