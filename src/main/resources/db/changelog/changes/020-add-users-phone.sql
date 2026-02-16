-- Add optional phone column to users in existing tenant schemas (for WhatsApp notifications)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE IF EXISTS %I.users ADD COLUMN IF NOT EXISTS phone VARCHAR(50)', r.schema_name);
    END LOOP;
END;
$$;
