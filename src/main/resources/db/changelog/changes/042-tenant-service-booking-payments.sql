-- Payments for service appointments (bookings) - M-Pesa STK Push
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.service_booking_payments (
            payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            appointment_id UUID NOT NULL REFERENCES %I.service_appointments(appointment_id) ON DELETE CASCADE,
            user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE RESTRICT,
            amount DECIMAL(15, 2) NOT NULL,
            transaction_id TEXT,
            payment_status VARCHAR(20) NOT NULL DEFAULT ''pending'' CHECK (payment_status IN (''pending'', ''completed'', ''failed'', ''cancelled'')),
            payment_method VARCHAR(50) DEFAULT ''M-Pesa'',
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_booking_payments_appointment ON %I.service_booking_payments(appointment_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_booking_payments_transaction ON %I.service_booking_payments(transaction_id)', r.schema_name);
    END LOOP;
END;
$$;
