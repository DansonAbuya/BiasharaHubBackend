-- Trust & Safety: delivery proof (signature, photo, GPS) on shipments
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS delivery_signature_url VARCHAR(1024)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS delivery_photo_url VARCHAR(1024)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS delivery_gps_lat DECIMAL(12, 8)', r.schema_name);
        EXECUTE format('ALTER TABLE IF EXISTS %I.shipments ADD COLUMN IF NOT EXISTS delivery_gps_lng DECIMAL(12, 8)', r.schema_name);
    END LOOP;
END;
$$;
