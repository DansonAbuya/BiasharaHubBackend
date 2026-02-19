-- Extend payment_status CHECK constraint to allow 'cancelled' in all tenant schemas
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        BEGIN
            EXECUTE format('ALTER TABLE %I.payments DROP CONSTRAINT IF EXISTS payments_payment_status_check', r.schema_name);
        EXCEPTION
            WHEN undefined_table THEN
                NULL;
        END;

        EXECUTE format(
            'ALTER TABLE IF EXISTS %I.payments
               ADD CONSTRAINT payments_payment_status_check
                   CHECK (payment_status IN (''pending'',''completed'',''failed'',''cancelled''))',
            r.schema_name
        );
    END LOOP;
END;
$$;

