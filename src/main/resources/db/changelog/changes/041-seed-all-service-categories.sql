-- Seed all possible service categories in every tenant schema (idempotent)
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('INSERT INTO %I.service_categories (category_id, name, display_order) VALUES
            (gen_random_uuid(), ''Consulting'', 1),
            (gen_random_uuid(), ''Repair & Maintenance'', 2),
            (gen_random_uuid(), ''Training'', 3),
            (gen_random_uuid(), ''Health & Wellness'', 4),
            (gen_random_uuid(), ''Beauty & Personal Care'', 5),
            (gen_random_uuid(), ''Legal'', 6),
            (gen_random_uuid(), ''Accounting & Finance'', 7),
            (gen_random_uuid(), ''Cleaning'', 8),
            (gen_random_uuid(), ''Events'', 9),
            (gen_random_uuid(), ''IT & Tech Support'', 10),
            (gen_random_uuid(), ''Photography & Videography'', 11),
            (gen_random_uuid(), ''Catering & Food Service'', 12),
            (gen_random_uuid(), ''Security Services'', 13),
            (gen_random_uuid(), ''Logistics & Delivery'', 14),
            (gen_random_uuid(), ''Real Estate'', 15),
            (gen_random_uuid(), ''Insurance'', 16),
            (gen_random_uuid(), ''Marketing & Advertising'', 17),
            (gen_random_uuid(), ''Design (Graphic, Interior, Web)'', 18),
            (gen_random_uuid(), ''Writing & Editing'', 19),
            (gen_random_uuid(), ''Translation & Interpretation'', 20),
            (gen_random_uuid(), ''Tutoring & Education'', 21),
            (gen_random_uuid(), ''Coaching & Mentoring'', 22),
            (gen_random_uuid(), ''Fitness & Personal Training'', 23),
            (gen_random_uuid(), ''Pet Care'', 24),
            (gen_random_uuid(), ''Gardening & Landscaping'', 25),
            (gen_random_uuid(), ''Plumbing'', 26),
            (gen_random_uuid(), ''Electrical'', 27),
            (gen_random_uuid(), ''HVAC & Cooling'', 28),
            (gen_random_uuid(), ''Moving & Relocation'', 29),
            (gen_random_uuid(), ''Storage'', 30),
            (gen_random_uuid(), ''Printing & Copying'', 31),
            (gen_random_uuid(), ''Tailoring & Alterations'', 32),
            (gen_random_uuid(), ''Vehicle Repair & Auto Service'', 33),
            (gen_random_uuid(), ''Salon & Barbershop'', 34),
            (gen_random_uuid(), ''Spa & Massage'', 35),
            (gen_random_uuid(), ''Medical & Dental'', 36),
            (gen_random_uuid(), ''Therapy & Counseling'', 37),
            (gen_random_uuid(), ''Childcare & Nanny'', 38),
            (gen_random_uuid(), ''Elderly Care'', 39),
            (gen_random_uuid(), ''Event Planning'', 40),
            (gen_random_uuid(), ''DJ & Entertainment'', 41),
            (gen_random_uuid(), ''Videography'', 42),
            (gen_random_uuid(), ''Software Development'', 43),
            (gen_random_uuid(), ''Digital Marketing'', 44),
            (gen_random_uuid(), ''SEO & Content'', 45),
            (gen_random_uuid(), ''Administrative & Virtual Assistant'', 46),
            (gen_random_uuid(), ''Other'', 99)
        ON CONFLICT (name) DO NOTHING', r.schema_name);
    END LOOP;
END;
$$;
