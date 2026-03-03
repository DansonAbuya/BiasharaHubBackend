-- Clean up legacy users_role_check constraints in tenant schemas.
-- Some schemas ended up with BOTH:
--   - users_role_check          (without 'supplier')
--   - users_role_check_supplier (with 'supplier')
-- Because both must be satisfied, inserts with role = 'supplier' still fail.
--
-- This script drops ONLY the old users_role_check in every tenant schema,
-- leaving users_role_check_supplier (which already allows 'supplier') intact.

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT schema_name
        FROM public.tenants
        WHERE schema_name IS NOT NULL
    LOOP
        -- Drop the legacy role check constraint if it exists in this tenant schema.
        EXECUTE format('ALTER TABLE %I.users DROP CONSTRAINT IF EXISTS users_role_check', r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;

