-- Add DISPATCHED status (supplier confirms dispatch) and received_quantity (seller confirms what was actually received)
DO $$
DECLARE
    r RECORD;
    cname TEXT;
BEGIN
    -- 1. Add DISPATCHED to supplier_deliveries status
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        SELECT con.conname INTO cname
        FROM pg_constraint con
        JOIN pg_class tbl ON tbl.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = tbl.relnamespace
        WHERE con.contype = 'c' AND nsp.nspname = r.schema_name AND tbl.relname = 'supplier_deliveries'
          AND (pg_get_constraintdef(con.oid) LIKE '%status%' OR pg_get_constraintdef(con.oid) LIKE '%DRAFT%');
        IF cname IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I.supplier_deliveries DROP CONSTRAINT %I', r.schema_name, cname);
        END IF;
        EXECUTE format(
            'ALTER TABLE %I.supplier_deliveries ADD CONSTRAINT supplier_deliveries_status_check CHECK (status IN (''DRAFT'', ''DISPATCHED'', ''PROCESSING'', ''RECEIVED''))',
            r.schema_name
        );
    END LOOP;

    -- 2. Add received_quantity to supplier_delivery_items (seller-confirmed; null = assume same as quantity)
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format(
            'ALTER TABLE %I.supplier_delivery_items ADD COLUMN IF NOT EXISTS received_quantity INTEGER',
            r.schema_name
        );
    END LOOP;
END;
$$;
