-- BiasharaHub Services: physical service bookings (customer books appointment then attends)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.service_appointments (
            appointment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            service_id UUID NOT NULL REFERENCES %I.service_offerings(service_id) ON DELETE CASCADE,
            user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE RESTRICT,
            requested_date DATE NOT NULL,
            requested_time TIME,
            status VARCHAR(20) NOT NULL DEFAULT ''PENDING'' CHECK (status IN (''PENDING'', ''CONFIRMED'', ''COMPLETED'', ''CANCELLED'', ''NO_SHOW'')),
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_appointments_service ON %I.service_appointments(service_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_appointments_user ON %I.service_appointments(user_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_appointments_date ON %I.service_appointments(requested_date)', r.schema_name);
    END LOOP;
END;
$$;
