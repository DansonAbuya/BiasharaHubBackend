-- Existing tenants: extend users.role CHECK constraint to allow 'supplier'
DO $$
DECLARE
    r RECORD;
    v_constraint_name TEXT;
BEGIN
    FOR r IN
        SELECT schema_name
        FROM public.tenants
        WHERE schema_name IS NOT NULL
    LOOP
        -- Find existing CHECK constraint on users.role
        SELECT c.conname
        INTO v_constraint_name
        FROM pg_constraint c
                 JOIN pg_class tbl ON tbl.oid = c.conrelid
                 JOIN pg_namespace nsp ON nsp.oid = tbl.relnamespace
        WHERE c.contype = 'c'
          AND nsp.nspname = r.schema_name
          AND tbl.relname = 'users'
          AND pg_get_constraintdef(c.oid) ILIKE '%role%' AND pg_get_constraintdef(c.oid) ILIKE '%IN (%';

        IF v_constraint_name IS NOT NULL THEN
            EXECUTE format('ALTER TABLE %I.users DROP CONSTRAINT %I', r.schema_name, v_constraint_name);
        END IF;

        -- Add new CHECK constraint including supplier role
        EXECUTE format(
                'ALTER TABLE %I.users ADD CONSTRAINT users_role_check_supplier CHECK (role IN (''super_admin'', ''owner'', ''staff'', ''customer'', ''assistant_admin'', ''courier'', ''supplier''))',
                r.schema_name
            );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

