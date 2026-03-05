-- Track how much of a delivery item has been consumed by conversions before stock is added.
-- This lets us convert on the delivery first, then add only the remaining quantity to stock.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('ALTER TABLE %I.supplier_delivery_items ADD COLUMN IF NOT EXISTS converted_quantity INTEGER', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

