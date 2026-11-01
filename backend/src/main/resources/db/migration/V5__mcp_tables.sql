-- V5: Model Context Protocol (MCP) Tokens and Call Logs with Row-Level Security

CREATE TABLE IF NOT EXISTS mcp_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    client_platform VARCHAR(50) NOT NULL, -- CLAUDE, CHATGPT, ANTIGRAVITY, OTHER
    token_name VARCHAR(100) NOT NULL,
    token_prefix VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE, -- SHA-256 of the raw token
    permissions TEXT[] NOT NULL,
    is_revoked BOOLEAN NOT NULL DEFAULT false,
    is_paused BOOLEAN NOT NULL DEFAULT false,
    rate_limit_per_minute INT NOT NULL DEFAULT 60,
    expires_at TIMESTAMPTZ NULL,
    last_used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mcp_tokens_project_id ON mcp_tokens(project_id);
CREATE INDEX idx_mcp_tokens_hash ON mcp_tokens(token_hash);

CREATE TABLE IF NOT EXISTS mcp_call_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    token_id UUID REFERENCES mcp_tokens(id) ON DELETE SET NULL,
    tool_name VARCHAR(100) NOT NULL,
    client_platform VARCHAR(50) NOT NULL,
    duration_ms BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL, -- SUCCESS, ERROR, RATE_LIMITED, UNAUTHORIZED
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_mcp_call_logs_project ON mcp_call_logs(project_id, created_at DESC);
CREATE INDEX idx_mcp_call_logs_token ON mcp_call_logs(token_id);

-- Enforce Row-Level Security on MCP tables
ALTER TABLE mcp_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE mcp_tokens FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS mcp_tokens_isolation_policy ON mcp_tokens;
CREATE POLICY mcp_tokens_isolation_policy ON mcp_tokens
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE mcp_call_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE mcp_call_logs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS mcp_call_logs_isolation_policy ON mcp_call_logs;
CREATE POLICY mcp_call_logs_isolation_policy ON mcp_call_logs
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- Grant permissions to tenant role if exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
        GRANT ALL PRIVILEGES ON mcp_tokens TO aether_app;
        GRANT ALL PRIVILEGES ON mcp_call_logs TO aether_app;
    END IF;
END;
$$;

