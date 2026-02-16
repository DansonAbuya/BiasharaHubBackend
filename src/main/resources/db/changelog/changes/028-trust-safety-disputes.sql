-- Trust & Safety: disputes table (per tenant) for dispute workflow
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I.disputes (
                dispute_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                order_id UUID NOT NULL REFERENCES %I.orders(order_id) ON DELETE CASCADE,
                reporter_user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE RESTRICT,
                dispute_type VARCHAR(32) NOT NULL,
                status VARCHAR(32) NOT NULL DEFAULT ''open'',
                description TEXT,
                delivery_proof_url VARCHAR(1024),
                seller_response TEXT,
                seller_responded_at TIMESTAMP WITH TIME ZONE,
                resolved_at TIMESTAMP WITH TIME ZONE,
                resolved_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL,
                resolution VARCHAR(32),
                strike_reason VARCHAR(32),
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            )',
            r.schema_name, r.schema_name, r.schema_name, r.schema_name
        );
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_disputes_order ON %I.disputes(order_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_disputes_status ON %I.disputes(status)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_disputes_reporter ON %I.disputes(reporter_user_id)', r.schema_name);
    END LOOP;
END;
$$;
