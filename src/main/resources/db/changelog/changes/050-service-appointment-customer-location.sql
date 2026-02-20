-- Customer location for service appointments (physical/in-person services).
-- Allows customer to specify where they want the service delivered.

ALTER TABLE tenant_default.service_appointments ADD COLUMN IF NOT EXISTS customer_location_lat DOUBLE PRECISION;
ALTER TABLE tenant_default.service_appointments ADD COLUMN IF NOT EXISTS customer_location_lng DOUBLE PRECISION;
ALTER TABLE tenant_default.service_appointments ADD COLUMN IF NOT EXISTS customer_location_description TEXT;
