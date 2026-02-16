-- Add assigned_courier_id to tenant shipments tables for courier portal.
-- Expand users role CHECK to allow assistant_admin and courier.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        -- Add assigned_courier_id column with FK to users
        EXECUTE format(
            'ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS assigned_courier_id UUID REFERENCES %I.users(user_id)',
            r.schema_name, r.schema_name
        );

        -- Drop existing role CHECK and add expanded one (assistant_admin, courier)
        EXECUTE format('ALTER TABLE %I.users DROP CONSTRAINT IF EXISTS users_role_check', r.schema_name);
        EXECUTE format(
            'ALTER TABLE %I.users ADD CONSTRAINT users_role_check CHECK (role IN (''super_admin'', ''owner'', ''staff'', ''customer'', ''assistant_admin'', ''courier''))',
            r.schema_name
        );

        -- Index for courier portal queries (find by assigned_courier_id)
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_shipments_assigned_courier ON %I.shipments(assigned_courier_id)', r.schema_name);
    END LOOP;
END;
$$;
