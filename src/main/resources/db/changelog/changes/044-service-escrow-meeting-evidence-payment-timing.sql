-- Virtual services: meeting link, evidence, escrow. Physical: payment timing (before/after/contract).
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        -- service_offerings: meeting link and payment timing
        EXECUTE format('ALTER TABLE %I.service_offerings ADD COLUMN IF NOT EXISTS meeting_link TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_offerings ADD COLUMN IF NOT EXISTS meeting_details TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_offerings ADD COLUMN IF NOT EXISTS payment_timing VARCHAR(30) DEFAULT ''BEFORE_BOOKING''', r.schema_name);

        -- service_appointments: meeting link sent, evidence, confirm/dispute timestamps, escrow status
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS meeting_link_sent_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS evidence_url TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS evidence_notes TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS provider_marked_provided_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS customer_confirmed_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS customer_disputed_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS escrow_status VARCHAR(20)', r.schema_name);

        -- Extend status: drop old check if exists (name may vary), add new check
        EXECUTE format('ALTER TABLE %I.service_appointments DROP CONSTRAINT IF EXISTS service_appointments_status_check', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments DROP CONSTRAINT IF EXISTS service_appointments_status_check1', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD CONSTRAINT service_appointments_status_check CHECK (status IN (''PENDING'', ''CONFIRMED'', ''COMPLETED'', ''CANCELLED'', ''NO_SHOW'', ''SERVICE_PROVIDED'', ''CUSTOMER_CONFIRMED'', ''CUSTOMER_DISPUTED''))', r.schema_name);

        -- service_booking_escrow: hold funds for virtual until confirm or refund
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.service_booking_escrow (
            escrow_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            appointment_id UUID NOT NULL REFERENCES %I.service_appointments(appointment_id) ON DELETE CASCADE,
            booking_payment_id UUID NOT NULL REFERENCES %I.service_booking_payments(payment_id) ON DELETE RESTRICT,
            amount DECIMAL(15, 2) NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT ''HELD'' CHECK (status IN (''HELD'', ''RELEASED'', ''REFUNDED'')),
            released_at TIMESTAMP WITH TIME ZONE,
            refunded_at TIMESTAMP WITH TIME ZONE,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE UNIQUE INDEX IF NOT EXISTS idx_service_booking_escrow_appointment ON %I.service_booking_escrow(appointment_id)', r.schema_name);
    END LOOP;
END;
$$;
