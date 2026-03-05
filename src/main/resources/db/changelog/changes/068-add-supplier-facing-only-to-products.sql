-- Mark products that are only for supplier-facing flows (e.g. created from purchase orders)
-- so they don't appear on the seller's Products page as customer-facing items.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('ALTER TABLE %I.products ADD COLUMN IF NOT EXISTS supplier_facing_only BOOLEAN NOT NULL DEFAULT false', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

