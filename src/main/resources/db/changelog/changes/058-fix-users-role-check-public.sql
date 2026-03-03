-- Ensure the base public.users table allows 'supplier' as a valid role,
-- but only if public.users actually exists in this environment.
-- Some deployments keep all user tables in tenant schemas only.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.tables
        WHERE table_schema = 'public'
          AND table_name = 'users'
    ) THEN
        ALTER TABLE public.users
            DROP CONSTRAINT IF EXISTS users_role_check;

        ALTER TABLE public.users
            ADD CONSTRAINT users_role_check_supplier
            CHECK (role IN (
                'super_admin',
                'owner',
                'staff',
                'customer',
                'assistant_admin',
                'courier',
                'supplier'
            ));
    END IF;
END;
$$;

