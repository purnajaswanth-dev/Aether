-- V12: Runtime Role Schema History Isolation and Allowlist Hardening
-- Enforces zero access on flyway_schema_history and strips non-DML privileges from aether_app

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
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
        -- 1. Strictly isolate flyway_schema_history from runtime application queries
        IF to_regclass('public.flyway_schema_history') IS NOT NULL THEN
            REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM aether_app;
            REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history FROM PUBLIC;
        END IF;

        -- 2. Enforce explicit least-privilege DML on application tables (strip TRUNCATE, TRIGGER, REFERENCES)
        FOREACH tbl IN ARRAY app_tables LOOP
            IF to_regclass('public.' || quote_ident(tbl)) IS NOT NULL THEN
                EXECUTE format('REVOKE ALL PRIVILEGES ON TABLE public.%I FROM aether_app;', tbl);
                EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.%I TO aether_app;', tbl);
            END IF;
        END LOOP;
    END IF;
END;
$$;

