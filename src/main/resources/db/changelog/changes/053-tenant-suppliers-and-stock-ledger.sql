-- Add supplier tracking + stock ledger for traceability (all existing tenant schemas)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        -- Suppliers (per business)
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.suppliers (
            supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            business_id UUID NOT NULL,
            name VARCHAR(255) NOT NULL,
            phone VARCHAR(50),
            email VARCHAR(255),
            created_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_suppliers_business_id ON %I.suppliers(business_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_suppliers_name ON %I.suppliers(name)', r.schema_name);

        -- Supplier deliveries (what supplier delivered / what was received)
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.supplier_deliveries (
            delivery_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            business_id UUID NOT NULL,
            supplier_id UUID REFERENCES %I.suppliers(supplier_id) ON DELETE SET NULL,
            delivery_note_ref VARCHAR(255),
            delivered_at TIMESTAMP WITH TIME ZONE,
            received_at TIMESTAMP WITH TIME ZONE,
            received_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL,
            status VARCHAR(32) NOT NULL DEFAULT ''DRAFT'' CHECK (status IN (''DRAFT'', ''DISPATCHED'', ''PROCESSING'', ''RECEIVED'')),
            created_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_deliveries_business_id ON %I.supplier_deliveries(business_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_deliveries_supplier_id ON %I.supplier_deliveries(supplier_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_deliveries_status ON %I.supplier_deliveries(status)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_deliveries_created_at ON %I.supplier_deliveries(created_at)', r.schema_name);

        -- Delivery items
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.supplier_delivery_items (
            item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            delivery_id UUID NOT NULL REFERENCES %I.supplier_deliveries(delivery_id) ON DELETE CASCADE,
            product_id UUID NOT NULL REFERENCES %I.products(product_id) ON DELETE RESTRICT,
            product_name VARCHAR(255) NOT NULL,
            quantity INTEGER NOT NULL CHECK (quantity > 0),
            unit_cost DECIMAL(15, 2),
            received_quantity INTEGER,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_delivery_items_delivery_id ON %I.supplier_delivery_items(delivery_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_supplier_delivery_items_product_id ON %I.supplier_delivery_items(product_id)', r.schema_name);

        -- Stock ledger entries (audit trail for stock changes)
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.stock_ledger_entries (
            entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            business_id UUID NOT NULL,
            product_id UUID NOT NULL REFERENCES %I.products(product_id) ON DELETE RESTRICT,
            change_qty INTEGER NOT NULL,
            previous_qty INTEGER,
            new_qty INTEGER,
            entry_type VARCHAR(32) NOT NULL,
            supplier_id UUID REFERENCES %I.suppliers(supplier_id) ON DELETE SET NULL,
            delivery_id UUID REFERENCES %I.supplier_deliveries(delivery_id) ON DELETE SET NULL,
            order_id UUID REFERENCES %I.orders(order_id) ON DELETE SET NULL,
            performed_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL,
            note TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name, r.schema_name, r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_stock_ledger_entries_business_id ON %I.stock_ledger_entries(business_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_stock_ledger_entries_product_id ON %I.stock_ledger_entries(product_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_stock_ledger_entries_delivery_id ON %I.stock_ledger_entries(delivery_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_stock_ledger_entries_order_id ON %I.stock_ledger_entries(order_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_stock_ledger_entries_performed_by ON %I.stock_ledger_entries(performed_by_user_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_stock_ledger_entries_created_at ON %I.stock_ledger_entries(created_at)', r.schema_name);
    END LOOP;
END;
$$;

