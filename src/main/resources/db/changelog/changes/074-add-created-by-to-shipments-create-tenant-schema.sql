-- Ensure shipments.created_by_user_id exists in every tenant schema (idempotent).
-- Covers tenants created after 063 whose schema was created without this column.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants WHERE schema_name IS NOT NULL
    LOOP
        EXECUTE format('ALTER TABLE %I.shipments ADD COLUMN IF NOT EXISTS created_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL', r.schema_name, r.schema_name);
    END LOOP;
END;
$$ LANGUAGE plpgsql;
