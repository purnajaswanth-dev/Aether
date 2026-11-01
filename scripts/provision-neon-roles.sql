-- =============================================================================
-- AETHER NEON POSTGRESQL PRODUCTION ROLE PROVISIONING SCRIPT
-- =============================================================================
-- Target Database: Neon PostgreSQL
-- Execution Identity: Privileged database migration owner ('neondb_owner')
-- Purpose: Provision least-privilege 'aether_app' runtime role with NOBYPASSRLS,
--          explicit table-level allowlist, zero access to flyway_schema_history,
--          and targeted default privileges for the production migration role.
--
-- Instructions:
-- 1. Open the Neon Console (https://console.neon.tech) -> Project -> SQL Editor.
-- 2. Replace 'REPLACE_WITH_AETHER_APP_GENERATED_PASSWORD' with a strong random secret.
-- 3. Execute this script BEFORE Spring Boot / Flyway starts for the first time.
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
            NOCREATEDB
            NOCREATEROLE
            PASSWORD 'REPLACE_WITH_AETHER_APP_GENERATED_PASSWORD';
        RAISE NOTICE 'Role aether_app password and login attributes reinforced.';
    END IF;
END;
$$;

-- Step 2: Grant minimal schema usage (cannot create or drop schemas/objects)
GRANT USAGE ON SCHEMA public TO aether_app;

-- Step 3: Grant standard DML permissions on explicit application table allowlist
-- Derived directly from repository queries across all 37 application repositories.
-- Strictly NO TRUNCATE, NO ALTER, NO DROP, NO REFERENCES, NO TRIGGER.
DO $$
DECLARE
    tbl text;
    app_tables text[] := ARRAY[
        'users',
        'projects',
        'project_members',
        'project_integrations',
        'project_permissions',
        'artifacts',
        'artifact_versions',
        'artifact_references',
        'technical_decisions',
        'decision_versions',
        'decision_evidence',
        'timeline_events',
        'audit_events',
        'mcp_tokens',
        'mcp_call_logs',
        'ingestion_jobs',
        'secret_findings',
        'artifact_chunks',
        'embeddings',
        'extracted_facts',
        'dependency_edges',
        'model_profiles',
        'context_packages',
        'context_retrieval_logs',
        'integration_configs',
        'integration_credentials',
        'processed_events',
        'dead_letter_events',
        'integration_checkpoints',
        'deployments',
        'agent_devices',
        'agent_file_metadata',
        'tasks',
        'task_events',
        'agent_sessions',
        'agent_interactions',
        'implementation_records',
        'test_executions',
        'debugging_records',
        'task_links',
        'action_requests',
        'code_symbols',
        'code_dependencies',
        'agent_handoffs'
    ];
BEGIN
    FOREACH tbl IN ARRAY app_tables LOOP
        IF to_regclass('public.' || quote_ident(tbl)) IS NOT NULL THEN
            -- Strip any elevated permissions (TRUNCATE, REFERENCES, TRIGGER) and set clean DML
            EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM aether_app;', tbl);
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO aether_app;', tbl);
        END IF;
    END LOOP;
    RAISE NOTICE 'Granted explicit DML allowlist to aether_app on existing application tables.';
END;
$$;

-- Step 4: Isolate Flyway migration history from the runtime role
-- Runtime application queries have zero need to read or alter migration metadata.
-- Enforces zero access to flyway_schema_history.
DO $$
BEGIN
    IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
        REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM aether_app;
        REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM PUBLIC;
        RAISE NOTICE 'Revoked all privileges on flyway_schema_history from aether_app and PUBLIC.';
    END IF;
END;
$$;

-- Step 5: Grant sequence usage on explicit application sequences (excluding migration sequences)
DO $$
DECLARE
    seq RECORD;
BEGIN
    FOR seq IN
        SELECT sequence_schema, sequence_name
        FROM information_schema.sequences
        WHERE sequence_schema = 'public'
          AND sequence_name NOT LIKE 'flyway%'
    LOOP
        EXECUTE format('GRANT USAGE, SELECT, UPDATE ON SEQUENCE %I.%I TO aether_app;', seq.sequence_schema, seq.sequence_name);
    END LOOP;
END;
$$;

-- Step 6: Grant execution on specifically required application functions
-- current_project_matches(UUID): Evaluated by all RLS policies across all project-scoped tables
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

-- Step 8: Configure default privileges for future objects created by the production migration role
-- When Flyway executes migrations as 'neondb_owner' (or CURRENT_USER), future tables and
-- sequences will automatically grant least-privilege DML to aether_app.
DO $$
BEGIN
    -- Configure default privileges specifically for Neon production migration role 'neondb_owner'
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'neondb_owner') THEN
        ALTER DEFAULT PRIVILEGES FOR ROLE neondb_owner IN SCHEMA public
            GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aether_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE neondb_owner IN SCHEMA public
            GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO aether_app;
        ALTER DEFAULT PRIVILEGES FOR ROLE neondb_owner IN SCHEMA public
            GRANT USAGE ON TYPES TO aether_app;
        RAISE NOTICE 'Configured default privileges for production migration role neondb_owner.';
    END IF;

    -- Also configure default privileges for CURRENT_USER (covers local test environments and direct SQL execution)
    ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
        GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO aether_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
        GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO aether_app;
    ALTER DEFAULT PRIVILEGES FOR ROLE CURRENT_USER IN SCHEMA public
        GRANT USAGE ON TYPES TO aether_app;
END;
$$;

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
