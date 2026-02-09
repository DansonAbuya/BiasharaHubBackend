-- BiasharaHub: Function to create a new tenant schema with all required tables
CREATE OR REPLACE FUNCTION public.create_tenant_schema(
    p_tenant_id UUID,
    p_schema_name VARCHAR(63)
) RETURNS void AS $$
DECLARE
    v_schema VARCHAR(63);
BEGIN
    v_schema := LOWER(REGEXP_REPLACE(p_schema_name, '[^a-zA-Z0-9_]', '_', 'g'));
    IF LENGTH(v_schema) > 63 THEN
        v_schema := LEFT(v_schema, 63);
    END IF;
    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.users (user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), email VARCHAR(255) NOT NULL, password_hash VARCHAR(255) NOT NULL, name TEXT, role VARCHAR(50) NOT NULL DEFAULT ''customer'' CHECK (role IN (''super_admin'', ''owner'', ''staff'', ''customer'')), two_factor_enabled BOOLEAN DEFAULT false, business_id UUID, business_name VARCHAR(255), created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, UNIQUE(email))', v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.verification_codes (code_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE CASCADE, verification_code VARCHAR(10) NOT NULL, expires_at TIMESTAMP WITH TIME ZONE NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.password_reset_tokens (token_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE CASCADE, token VARCHAR(255) UNIQUE, token_hash VARCHAR(64), expires_at TIMESTAMP WITH TIME ZONE NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.product_categories (category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(100) NOT NULL UNIQUE, display_order INTEGER NOT NULL DEFAULT 0)', v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.products (product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), name VARCHAR(255) NOT NULL, category VARCHAR(100), price DECIMAL(15, 2) NOT NULL, quantity INTEGER NOT NULL DEFAULT 0, description TEXT, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, business_id UUID)', v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.inventory_images (image_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), product_id UUID NOT NULL REFERENCES %I.products(product_id) ON DELETE CASCADE, image_url VARCHAR(500) NOT NULL, is_main BOOLEAN DEFAULT false, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.orders (order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE RESTRICT, order_number VARCHAR(50) NOT NULL UNIQUE, total_amount DECIMAL(15, 2) NOT NULL, order_status VARCHAR(50) NOT NULL DEFAULT ''pending'' CHECK (order_status IN (''pending'', ''confirmed'', ''processing'', ''shipped'', ''delivered'', ''cancelled'')), shipping_address TEXT, ordered_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.order_items (order_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES %I.orders(order_id) ON DELETE CASCADE, product_id UUID NOT NULL REFERENCES %I.products(product_id) ON DELETE RESTRICT, inventory_image_id UUID REFERENCES %I.inventory_images(image_id) ON DELETE SET NULL, quantity INTEGER NOT NULL CHECK (quantity > 0), price_at_order DECIMAL(15, 2) NOT NULL, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema, v_schema, v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.payments (payment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES %I.orders(order_id) ON DELETE RESTRICT, user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE RESTRICT, amount DECIMAL(15, 2) NOT NULL, transaction_id TEXT, payment_status VARCHAR(50) NOT NULL DEFAULT ''pending'' CHECK (payment_status IN (''pending'', ''completed'', ''failed'')), payment_method VARCHAR(50) DEFAULT ''M-Pesa'', created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema, v_schema);
    EXECUTE format('CREATE TABLE IF NOT EXISTS %I.shipments (shipment_id UUID PRIMARY KEY DEFAULT gen_random_uuid(), order_id UUID NOT NULL REFERENCES %I.orders(order_id) ON DELETE RESTRICT, courier_service VARCHAR(100), tracking_number VARCHAR(100), status VARCHAR(50) NOT NULL DEFAULT ''pending'' CHECK (status IN (''pending'', ''shipped'', ''in_transit'', ''delivered'')), shipped_at TIMESTAMP WITH TIME ZONE, delivered_at TIMESTAMP WITH TIME ZONE, created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP)', v_schema, v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_users_email ON %I.users(email)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_verification_codes_user ON %I.verification_codes(user_id)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_verification_codes_expires ON %I.verification_codes(expires_at)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token ON %I.password_reset_tokens(token)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_token_hash ON %I.password_reset_tokens(token_hash)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_password_reset_tokens_expires ON %I.password_reset_tokens(expires_at)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_product_categories_display_order ON %I.product_categories(display_order)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_products_business_id ON %I.products(business_id)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_inventory_images_product ON %I.inventory_images(product_id)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_orders_user ON %I.orders(user_id)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_orders_status ON %I.orders(order_status)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_orders_ordered_at ON %I.orders(ordered_at)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_order_items_order ON %I.order_items(order_id)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_payments_order ON %I.payments(order_id)', v_schema);
    EXECUTE format('CREATE INDEX IF NOT EXISTS idx_shipments_order ON %I.shipments(order_id)', v_schema);
    UPDATE public.tenants SET schema_name = v_schema, updated_at = CURRENT_TIMESTAMP WHERE tenant_id = p_tenant_id;
END;
$$ LANGUAGE plpgsql;
