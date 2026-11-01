-- V4: Non-superuser tenant application role with NOBYPASSRLS for row-level security enforcement

DO $$
BEGIN
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
            CREATE ROLE aether_app LOGIN PASSWORD 'aether_app_password' NOBYPASSRLS;
        END IF;

        GRANT USAGE ON SCHEMA public TO aether_app;
        GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO aether_app;
        GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO aether_app;
        GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA public TO aether_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO aether_app;
        ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO aether_app;
    EXCEPTION WHEN insufficient_privilege THEN
        RAISE NOTICE 'Skipping aether_app role creation due to insufficient privileges on managed database service.';
    END;
END;
$$;

