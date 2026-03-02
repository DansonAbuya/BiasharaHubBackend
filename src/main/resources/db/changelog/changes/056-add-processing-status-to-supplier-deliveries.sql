-- Add PROCESSING status to supplier_deliveries (step between DRAFT and RECEIVED)
DO $$
DECLARE
    r RECORD;
    cname TEXT;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        -- Find the check constraint on supplier_deliveries (status check)
        SELECT con.conname INTO cname
        FROM pg_constraint con
        JOIN pg_class tbl ON tbl.oid = con.conrelid
        JOIN pg_namespace nsp ON nsp.oid = tbl.relnamespace
        WHERE con.contype = 'c'
          AND nsp.nspname = r.schema_name
          AND tbl.relname = 'supplier_deliveries'
          AND (pg_get_constraintdef(con.oid) LIKE '%status%' OR pg_get_constraintdef(con.oid) LIKE '%DRAFT%');

        IF cname IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I.supplier_deliveries DROP CONSTRAINT %I', r.schema_name, cname);
        END IF;

        EXECUTE format(
            'ALTER TABLE %I.supplier_deliveries ADD CONSTRAINT supplier_deliveries_status_check CHECK (status IN (''DRAFT'', ''PROCESSING'', ''RECEIVED''))',
            r.schema_name
        );
    END LOOP;
END;
$$;
