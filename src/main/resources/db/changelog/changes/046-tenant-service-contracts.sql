-- Service contracts: for payment_timing AS_PER_CONTRACT; terms and payment schedule, signed by both parties.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.service_contracts (
            contract_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            service_id UUID NOT NULL REFERENCES %I.service_offerings(service_id) ON DELETE CASCADE,
            appointment_id UUID REFERENCES %I.service_appointments(appointment_id) ON DELETE SET NULL,
            business_id UUID NOT NULL,
            customer_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE RESTRICT,
            terms TEXT NOT NULL,
            payment_schedule TEXT,
            status VARCHAR(30) NOT NULL DEFAULT ''DRAFT'' CHECK (status IN (''DRAFT'', ''PENDING_SIGNATURES'', ''SIGNED'', ''ACTIVE'', ''COMPLETED'')),
            signed_by_customer_at TIMESTAMP WITH TIME ZONE,
            signed_by_provider_at TIMESTAMP WITH TIME ZONE,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name, r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_contracts_service ON %I.service_contracts(service_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_contracts_appointment ON %I.service_contracts(appointment_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_contracts_customer ON %I.service_contracts(customer_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_contracts_business ON %I.service_contracts(business_id)', r.schema_name);
    END LOOP;
END;
$$;
