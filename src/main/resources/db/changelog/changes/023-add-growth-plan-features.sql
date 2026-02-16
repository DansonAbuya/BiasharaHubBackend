-- Add Growth plan feature columns to users in existing tenant schemas
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS growth_inventory_automation BOOLEAN DEFAULT false', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS growth_whatsapp_enabled BOOLEAN DEFAULT false', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS growth_analytics_enabled BOOLEAN DEFAULT false', r.schema_name);
        EXECUTE format('ALTER TABLE %I.users ADD COLUMN IF NOT EXISTS growth_delivery_integrations BOOLEAN DEFAULT false', r.schema_name);
    END LOOP;
END;
$$;
