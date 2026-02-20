-- BiasharaHub Services module: service_offerings table in all tenant schemas
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.service_offerings (
            service_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name VARCHAR(255) NOT NULL,
            category VARCHAR(100),
            description TEXT,
            price DECIMAL(15, 2) NOT NULL,
            business_id UUID NOT NULL,
            delivery_type VARCHAR(20) NOT NULL DEFAULT ''PHYSICAL'' CHECK (delivery_type IN (''VIRTUAL'', ''PHYSICAL'')),
            duration_minutes INTEGER,
            is_active BOOLEAN NOT NULL DEFAULT true,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_offerings_business_id ON %I.service_offerings(business_id)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_offerings_delivery_type ON %I.service_offerings(delivery_type)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_service_offerings_category ON %I.service_offerings(category)', r.schema_name);
    END LOOP;
END;
$$;
