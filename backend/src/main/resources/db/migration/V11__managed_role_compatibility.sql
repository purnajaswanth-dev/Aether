-- V11: Managed Database Role Compatibility and Permissions Hardening
-- Preserves RLS policies and project isolation while harmonizing schema permissions

DO $$
BEGIN
    -- Refresh standard permissions for CURRENT_USER (Neon owner / application connection role)
    BEGIN
        GRANT USAGE ON SCHEMA public TO CURRENT_USER;
        GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO CURRENT_USER;
        GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO CURRENT_USER;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO CURRENT_USER;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO CURRENT_USER;
    EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Skipping default privilege grant to CURRENT_USER: %', SQLERRM;
    END;

    -- If the dedicated aether_app tenant role exists, ensure it has strict least-privilege permissions without BYPASSRLS
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
        BEGIN
            GRANT USAGE ON SCHEMA public TO aether_app;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO aether_app;
            GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO aether_app;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aether_app;
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO aether_app;
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'Skipping default privilege grant to aether_app: %', SQLERRM;
        END;
    END IF;
END;
$$;
