-- Create notifications table for all existing tenant schemas
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.notifications (
            notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL REFERENCES %I.users(user_id) ON DELETE CASCADE,
            type VARCHAR(32) NOT NULL,
            title TEXT NOT NULL,
            message TEXT NOT NULL,
            action_url VARCHAR(255),
            data TEXT,
            read BOOLEAN NOT NULL DEFAULT false,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
            read_at TIMESTAMP WITH TIME ZONE
        )', r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON %I.notifications(user_id, created_at DESC)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_notifications_user_read ON %I.notifications(user_id, read)', r.schema_name);
    END LOOP;
END;
$$;

