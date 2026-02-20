-- Online service delivery methods for virtual/online services.
-- Allows service providers to specify how they deliver online services.
-- Stored as comma-separated values (e.g., 'VIDEO_CALL,PHONE_CALL,WHATSAPP').

ALTER TABLE tenant_default.service_offerings ADD COLUMN IF NOT EXISTS online_delivery_methods TEXT;

-- Add comments for documentation
COMMENT ON COLUMN tenant_default.service_offerings.online_delivery_methods IS 
    'Comma-separated delivery methods for online services: VIDEO_CALL, PHONE_CALL, WHATSAPP, LIVE_CHAT, EMAIL, SCREEN_SHARE, FILE_DELIVERY, RECORDED_CONTENT, SOCIAL_MEDIA';
