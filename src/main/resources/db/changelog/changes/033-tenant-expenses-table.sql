-- Create expenses table in all tenant schemas for micro-accounting
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT schema_name FROM public.tenants
    LOOP
        EXECUTE format('CREATE TABLE IF NOT EXISTS %I.expenses (
            expense_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            category VARCHAR(64) NOT NULL,
            amount DECIMAL(15, 2) NOT NULL,
            description TEXT,
            receipt_reference VARCHAR(255),
            expense_date DATE NOT NULL,
            created_by_user_id UUID REFERENCES %I.users(user_id) ON DELETE SET NULL,
            created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
        )', r.schema_name, r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_expenses_date ON %I.expenses(expense_date)', r.schema_name);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_expenses_category ON %I.expenses(category)', r.schema_name);
    END LOOP;
END;
$$;
