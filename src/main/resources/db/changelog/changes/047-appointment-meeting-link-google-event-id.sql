-- Per-appointment meeting link (e.g. from Google Meet) and Google Calendar event id
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS meeting_link TEXT', r.schema_name);
        EXECUTE format('ALTER TABLE %I.service_appointments ADD COLUMN IF NOT EXISTS google_event_id VARCHAR(255)', r.schema_name);
    END LOOP;
END;
$$;
