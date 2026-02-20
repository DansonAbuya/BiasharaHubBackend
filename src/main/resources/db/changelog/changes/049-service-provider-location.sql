-- Service provider location fields for physical/in-person services.
-- Required when service_delivery_type is 'PHYSICAL' or 'BOTH'.

ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_location_lat DOUBLE PRECISION;
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_location_lng DOUBLE PRECISION;
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_location_description TEXT;
