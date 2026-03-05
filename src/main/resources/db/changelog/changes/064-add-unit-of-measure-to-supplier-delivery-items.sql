-- Add unit_of_measure to supplier_delivery_items so seller sees unit (e.g. kg, g) and subdivision can use it.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('ALTER TABLE %I.supplier_delivery_items ADD COLUMN IF NOT EXISTS unit_of_measure VARCHAR(32)', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;
