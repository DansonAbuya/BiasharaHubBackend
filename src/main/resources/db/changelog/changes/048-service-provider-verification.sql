-- Service provider verification: separate journey from product seller (business) verification.
-- Owners can be verified as product sellers (verification_status) and/or as service providers (service_provider_status).

ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_provider_status VARCHAR(32) DEFAULT 'pending';
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_provider_notes TEXT;
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_provider_category_id UUID REFERENCES tenant_default.service_categories(category_id) ON DELETE SET NULL;
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_delivery_type VARCHAR(32);
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_provider_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE tenant_default.users ADD COLUMN IF NOT EXISTS service_provider_verified_by_user_id UUID REFERENCES tenant_default.users(user_id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS tenant_default.service_provider_documents (
    document_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES tenant_default.users(user_id) ON DELETE CASCADE,
    document_type VARCHAR(64) NOT NULL,
    file_url VARCHAR(500) NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_service_provider_docs_user ON tenant_default.service_provider_documents(user_id);
