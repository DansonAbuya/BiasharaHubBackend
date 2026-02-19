-- Extend existing tenant orders tables to support delivery mode and shipping fee
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        -- Add delivery_mode column with allowed values
        EXECUTE format('ALTER TABLE IF EXISTS %I.orders ADD COLUMN IF NOT EXISTS delivery_mode VARCHAR(50) NOT NULL DEFAULT ''SELLER_SELF''', r.schema_name);

        -- Add shipping_fee column (default 0)
        EXECUTE format('ALTER TABLE IF EXISTS %I.orders ADD COLUMN IF NOT EXISTS shipping_fee DECIMAL(15,2) NOT NULL DEFAULT 0', r.schema_name);
    END LOOP;
END;
$$;

