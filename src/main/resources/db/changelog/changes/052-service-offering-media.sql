-- Add media fields to service_offerings for images and videos
ALTER TABLE tenant_default.service_offerings ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE tenant_default.service_offerings ADD COLUMN IF NOT EXISTS video_url TEXT;
ALTER TABLE tenant_default.service_offerings ADD COLUMN IF NOT EXISTS gallery_urls TEXT;

COMMENT ON COLUMN tenant_default.service_offerings.image_url IS 'Main image URL to showcase this service';
COMMENT ON COLUMN tenant_default.service_offerings.video_url IS 'Optional video URL to demonstrate this service (YouTube, Vimeo, or direct link)';
COMMENT ON COLUMN tenant_default.service_offerings.gallery_urls IS 'Comma-separated list of additional image URLs for this service';
