-- Extend existing tenant shipments tables to match updated Shipment entity
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        -- Add delivery_mode column
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS delivery_mode VARCHAR(50) NOT NULL DEFAULT ''SELLER_SELF''', r.schema_name);

        -- Add rider marketplace columns
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS rider_name VARCHAR(255)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS rider_phone VARCHAR(50)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS rider_vehicle VARCHAR(100)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS rider_job_id VARCHAR(100)', r.schema_name);

        -- Add pickup and escrow/OTP columns
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS pickup_location TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS otp_code VARCHAR(10)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS otp_verified_at TIMESTAMP WITH TIME ZONE', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS escrow_released_at TIMESTAMP WITH TIME ZONE', r.schema_name);

        -- Relax and update status constraint to match new lifecycle
        BEGIN
            EXECUTE format('ALTER TABLE %I.shipments DROP CONSTRAINT IF EXISTS shipments_status_check', r.schema_name);
        EXCEPTION
            WHEN undefined_table THEN
                -- Schema might not have shipments yet; ignore
                NULL;
        END;

        EXECUTE format(
            'ALTER TABLE IF EXISTS %I.shipments
               ALTER COLUMN status SET DEFAULT ''CREATED'',
               ALTER COLUMN status TYPE VARCHAR(50),
               ADD CONSTRAINT shipments_status_check
                   CHECK (status IN (''CREATED'',''PICKED_UP'',''IN_TRANSIT'',''OUT_FOR_DELIVERY'',''READY_FOR_PICKUP'',''DELIVERED'',''COLLECTED'',''ESCROW_RELEASED''))',
            r.schema_name
        );
    END LOOP;
END;
$$;

