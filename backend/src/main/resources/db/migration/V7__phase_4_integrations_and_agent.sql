-- V7: Phase 4 External Integrations, Deployments, and Local Agent
-- Supports GitHub, Render, Postman, Docker, and the Aether Local Project Agent

-- 1. Integration Configurations
CREATE TABLE IF NOT EXISTS integration_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL, -- GITHUB, RENDER, POSTMAN, DOCKER
    status VARCHAR(50) NOT NULL DEFAULT 'DISCONNECTED', -- CONNECTED, DISCONNECTED, PAUSED, ERROR
    config_metadata JSONB NOT NULL DEFAULT '{}',
    last_sync_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_integration_configs_project_provider ON integration_configs(project_id, provider);

-- 2. Integration Credentials (AES-256-GCM Envelope Encrypted)
CREATE TABLE IF NOT EXISTS integration_credentials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    integration_id UUID NOT NULL REFERENCES integration_configs(id) ON DELETE CASCADE,
    encrypted_secret BYTEA NOT NULL,
    initialization_vector BYTEA NOT NULL,
    key_version VARCHAR(50) NOT NULL DEFAULT 'v1',
    auth_tag BYTEA NOT NULL,
    masked_preview VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_integration_credentials_integration ON integration_credentials(integration_id);

-- 3. Processed Events (Idempotency and Deduplication)
CREATE TABLE IF NOT EXISTS processed_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    raw_artifact_id UUID NULL REFERENCES artifacts(id) ON DELETE SET NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PROCESSED',
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processed_events UNIQUE (project_id, provider, event_id)
);

CREATE INDEX IF NOT EXISTS idx_processed_events_idempotency ON processed_events(project_id, idempotency_key);
CREATE INDEX IF NOT EXISTS idx_processed_events_processed_at ON processed_events(project_id, processed_at DESC);

-- 4. Dead Letter Events (DLQ for failed webhooks and sync payloads)
CREATE TABLE IF NOT EXISTS dead_letter_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    headers JSONB NOT NULL DEFAULT '{}',
    error_message TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING', -- PENDING, RETRIED, DISCARDED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_retry_at TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_project_status ON dead_letter_events(project_id, status);

-- 5. Integration Checkpoints (Cursor and timestamp tracking)
CREATE TABLE IF NOT EXISTS integration_checkpoints (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    cursor_value VARCHAR(255) NOT NULL,
    checkpoint_timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_integration_checkpoints UNIQUE (project_id, provider, resource_id)
);

-- 6. Deployments
CREATE TABLE IF NOT EXISTS deployments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    service_id VARCHAR(255) NOT NULL,
    external_deploy_id VARCHAR(255) NOT NULL,
    environment VARCHAR(50) NOT NULL DEFAULT 'production',
    commit_sha VARCHAR(64) NULL,
    status VARCHAR(50) NOT NULL, -- CREATED, BUILD_IN_PROGRESS, LIVE, BUILD_FAILED, DEPLOY_FAILED, CANCELED
    failure_classification VARCHAR(50) NULL, -- BUILD_FAILURE, DEPLOY_FAILURE, RUNTIME_FAILURE, CONFIGURATION_ERROR, NONE
    build_logs_artifact_id UUID NULL REFERENCES artifacts(id) ON DELETE SET NULL,
    runtime_logs_artifact_id UUID NULL REFERENCES artifacts(id) ON DELETE SET NULL,
    extracted_errors JSONB NOT NULL DEFAULT '[]',
    linked_task_id UUID NULL,
    deploy_started_at TIMESTAMPTZ NULL,
    deploy_finished_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_deployments_provider_external_id UNIQUE (project_id, provider, external_deploy_id)
);

CREATE INDEX IF NOT EXISTS idx_deployments_project_created ON deployments(project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_deployments_service_status ON deployments(service_id, status);

-- 7. Agent Devices (Local Project Agent Pairing & Heartbeats)
CREATE TABLE IF NOT EXISTS agent_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    device_code VARCHAR(128) NOT NULL UNIQUE,
    user_code VARCHAR(32) NOT NULL,
    device_token_hash VARCHAR(128) NULL,
    hostname VARCHAR(255) NULL,
    os VARCHAR(100) NULL,
    local_path TEXT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PAIR', -- PENDING_PAIR, ACTIVE, PAUSED, DISCONNECTED
    expires_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_agent_devices_project ON agent_devices(project_id);

-- 8. Agent File Metadata (Local workspace index cache)
CREATE TABLE IF NOT EXISTS agent_file_metadata (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES agent_devices(id) ON DELETE CASCADE,
    relative_path TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    mtime TIMESTAMPTZ NOT NULL,
    language VARCHAR(50) NULL,
    symbols JSONB NOT NULL DEFAULT '[]',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_agent_file_metadata UNIQUE (project_id, device_id, relative_path)
);

CREATE INDEX IF NOT EXISTS idx_agent_file_metadata_path ON agent_file_metadata(project_id, relative_path);

-- Enable Row-Level Security on all 8 tables
DO $$
DECLARE
    tbl text;
    tables text[] := ARRAY[
        'integration_configs',
        'integration_credentials',
        'processed_events',
        'dead_letter_events',
        'integration_checkpoints',
        'deployments',
        'agent_devices',
        'agent_file_metadata'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY;', tbl);
        EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY;', tbl);
        EXECUTE format('DROP POLICY IF EXISTS %I ON %I;', tbl || '_isolation_policy', tbl);
        EXECUTE format(
            'CREATE POLICY %I ON %I FOR ALL USING (current_project_matches(project_id)) WITH CHECK (current_project_matches(project_id));',
            tbl || '_isolation_policy', tbl
        );
    END LOOP;
END;
$$;

-- Grant privileges to non-superuser tenant role if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
        GRANT ALL PRIVILEGES ON integration_configs TO aether_app;
        GRANT ALL PRIVILEGES ON integration_credentials TO aether_app;
        GRANT ALL PRIVILEGES ON processed_events TO aether_app;
        GRANT ALL PRIVILEGES ON dead_letter_events TO aether_app;
        GRANT ALL PRIVILEGES ON integration_checkpoints TO aether_app;
        GRANT ALL PRIVILEGES ON deployments TO aether_app;
        GRANT ALL PRIVILEGES ON agent_devices TO aether_app;
        GRANT ALL PRIVILEGES ON agent_file_metadata TO aether_app;
    END IF;
END;
$$;

