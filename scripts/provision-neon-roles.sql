-- =============================================================================
-- AETHER NEON POSTGRESQL PRODUCTION ROLE PROVISIONING SCRIPT
-- =============================================================================
-- Target Database: Neon PostgreSQL
-- Execution Identity: Privileged database owner (e.g., 'neondb_owner')
-- Purpose: Provision least-privilege 'aether_app' runtime role with NOBYPASSRLS.
--
-- Instructions:
-- 1. Open the Neon Console (https://console.neon.tech) -> Project -> SQL Editor.
-- 2. Replace 'REPLACE_WITH_AETHER_APP_GENERATED_PASSWORD' with a strong random secret.
-- 3. Execute this script.
-- =============================================================================

-- Step 1: Create or reinforce the dedicated unprivileged application role
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
        CREATE ROLE aether_app WITH
            LOGIN
            NOSUPERUSER
            NOBYPASSRLS
            NOCREATEDB
            NOCREATEROLE
            PASSWORD 'REPLACE_WITH_AETHER_APP_GENERATED_PASSWORD';
        RAISE NOTICE 'Role aether_app created successfully with NOBYPASSRLS.';
    ELSE
        ALTER ROLE aether_app WITH
            LOGIN
            NOSUPERUSER
            NOBYPASSRLS
            NOCREATEDB
            NOCREATEROLE
            PASSWORD 'REPLACE_WITH_AETHER_APP_GENERATED_PASSWORD';
        RAISE NOTICE 'Role aether_app attributes reinforced with NOBYPASSRLS.';
    END IF;
END;
$$;

-- Step 2: Grant minimal schema usage (cannot create or drop schemas/objects)
GRANT USAGE ON SCHEMA public TO aether_app;

-- Step 3: Grant standard DML permissions on application tables
-- Strictly NO TRUNCATE, NO ALTER, NO DROP, NO REFERENCES, NO TRIGGER
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO aether_app;

-- Step 4: Isolate Flyway migration history from the runtime role
-- Runtime application queries have zero need to read or alter migration metadata
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history') THEN
        REVOKE ALL PRIVILEGES ON TABLE flyway_schema_history FROM aether_app;
        RAISE NOTICE 'Revoked all privileges on flyway_schema_history from aether_app.';
    END IF;
END;
$$;

-- Step 5: Grant sequence usage (required for auto-incrementing serial identifiers)
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO aether_app;

-- Step 6: Grant execution on specifically required application functions
-- current_project_matches(UUID): Evaluated by all RLS policies across all 22 project-scoped tables
-- reject_update_delete(): Enforces immutability triggers on append-only audit and event logs
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'current_project_matches') THEN
        GRANT EXECUTE ON FUNCTION current_project_matches(UUID) TO aether_app;
    END IF;

    IF EXISTS (SELECT 1 FROM pg_proc WHERE proname = 'reject_update_delete') THEN
        GRANT EXECUTE ON FUNCTION reject_update_delete() TO aether_app;
    END IF;
END;
$$;

-- Step 7: Grant USAGE on extension data types (vector) if installed
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'vector') THEN
        GRANT USAGE ON TYPE vector TO aether_app;
    END IF;
END;
$$;

-- Step 8: Configure default privileges for future objects created by the migration role
-- When Flyway executes migrations as CURRENT_USER (neondb_owner), future tables and
-- sequences will automatically grant least-privilege DML to aether_app.
ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aether_app;

ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO aether_app;

ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
    GRANT USAGE ON TYPES TO aether_app;

-- Step 9: Verification query (inspect role attributes)
SELECT
    rolname,
    rolsuper,
    rolbypassrls,
    rolcreatedb,
    rolcreaterole
FROM pg_roles
WHERE rolname IN ('neondb_owner', 'aether_app')
ORDER BY rolname;
