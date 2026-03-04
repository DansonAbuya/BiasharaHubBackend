-- Separate receipt from updating stock: seller can confirm receipt anytime; stock is added only when previous dispatch is sold out.
-- stock_updated_at: when non-null, quantities from this delivery have been added to product stock.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('ALTER TABLE %I.supplier_deliveries ADD COLUMN IF NOT EXISTS stock_updated_at TIMESTAMP WITH TIME ZONE', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;
