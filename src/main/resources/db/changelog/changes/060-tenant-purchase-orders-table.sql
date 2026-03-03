-- Create purchase_orders and purchase_order_items tables in each tenant schema.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.purchase_orders (
                purchase_order_id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                business_id         UUID NOT NULL,
                supplier_id         UUID REFERENCES %I.suppliers(supplier_id),
                po_number           VARCHAR(64),
                delivery_note_ref   VARCHAR(255),
                expected_delivery_date TIMESTAMPTZ,
                status              VARCHAR(32) NOT NULL DEFAULT ''DRAFT'',
                created_by_user_id  UUID REFERENCES %I.users(user_id),
                created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
                updated_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
            );
        ', r.schema_name, r.schema_name, r.schema_name);

        EXECUTE format('
            CREATE TABLE IF NOT EXISTS %I.purchase_order_items (
                item_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                purchase_order_id   UUID NOT NULL REFERENCES %I.purchase_orders(purchase_order_id) ON DELETE CASCADE,
                product_id          UUID REFERENCES %I.products(product_id),
                description         TEXT,
                unit_of_measure     VARCHAR(32),
                requested_quantity  INTEGER NOT NULL,
                expected_unit_cost  NUMERIC(15,2),
                created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
            );
        ', r.schema_name, r.schema_name, r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

