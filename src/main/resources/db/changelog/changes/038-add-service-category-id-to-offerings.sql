-- Add service_category_id to service_offerings (FK to service_categories)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE %I.service_offerings ADD COLUMN IF NOT EXISTS service_category_id UUID REFERENCES %I.service_categories(category_id) ON DELETE SET NULL', r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_offerings_service_category_id ON %I.service_offerings(service_category_id)', r.schema_name);
    END LOOP;
END;
$$;
