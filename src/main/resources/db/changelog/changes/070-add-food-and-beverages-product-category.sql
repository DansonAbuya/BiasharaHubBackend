-- Add 'Food & Beverages' to product_categories for all existing tenant schemas.
-- Safe to run multiple times thanks to ON CONFLICT (name) DO NOTHING.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format(
            'INSERT INTO %I.product_categories (category_id, name, display_order)
             VALUES (gen_random_uuid(), ''Food & Beverages'', 10)
             ON CONFLICT (name) DO NOTHING',
            r.schema_name
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

