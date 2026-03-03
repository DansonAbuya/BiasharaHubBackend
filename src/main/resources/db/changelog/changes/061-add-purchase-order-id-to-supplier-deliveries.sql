-- Add purchase_order_id to supplier_deliveries so dispatches can be linked to a purchase order.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('
            ALTER TABLE %I.supplier_deliveries
            ADD COLUMN IF NOT EXISTS purchase_order_id UUID REFERENCES %I.purchase_orders(purchase_order_id) ON DELETE SET NULL
        ', r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_deliveries_purchase_order_id ON %I.supplier_deliveries(purchase_order_id)', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;
