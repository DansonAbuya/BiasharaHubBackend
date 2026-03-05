-- Link customer-facing subdivision products to their supplier-facing source product.
-- When a product is created from subdividing received stock, source_product_id points to the original (supplier-facing) product.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('ALTER TABLE %I.products ADD COLUMN IF NOT EXISTS source_product_id UUID', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;
