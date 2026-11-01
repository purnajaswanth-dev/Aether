-- V8__phase_5_workflow_coordination.sql
-- Phase 5: Workflow Coordination, Tasks, Handoffs, Evidence, Debugging & Code Intelligence

-- 1. Tasks Table
CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_number INT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'BACKLOG',
    priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    assignee_id UUID REFERENCES users(id) ON DELETE SET NULL,
    proposed_by_agent BOOLEAN NOT NULL DEFAULT FALSE,
    agent_proposal_reason TEXT,
    human_override_reason TEXT,
    lock_version INT NOT NULL DEFAULT 1,
    created_by UUID REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_tasks_project_number UNIQUE (project_id, task_number)
);

CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_project_number ON tasks(project_id, task_number);

ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks FORCE ROW LEVEL SECURITY;
CREATE POLICY tasks_project_isolation ON tasks
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 2. Task Events (Audit Log of Transitions)
CREATE TABLE IF NOT EXISTS task_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    event_type VARCHAR(50) NOT NULL,
    old_status VARCHAR(50),
    new_status VARCHAR(50),
    actor_type VARCHAR(50) NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    reason TEXT,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_task_events_task ON task_events(project_id, task_id, created_at);

ALTER TABLE task_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_events FORCE ROW LEVEL SECURITY;
CREATE POLICY task_events_project_isolation ON task_events
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 3. Agent Sessions
CREATE TABLE IF NOT EXISTS agent_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    client_platform VARCHAR(50) NOT NULL,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_sessions_project_task ON agent_sessions(project_id, task_id);

ALTER TABLE agent_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_sessions FORCE ROW LEVEL SECURITY;
CREATE POLICY agent_sessions_project_isolation ON agent_sessions
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 4. Agent Interactions (Tools called, Artifacts saved)
CREATE TABLE IF NOT EXISTS agent_interactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES agent_sessions(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    interaction_type VARCHAR(50) NOT NULL,
    tool_name VARCHAR(100),
    artifact_id UUID REFERENCES artifacts(id) ON DELETE SET NULL,
    details JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_interactions_session ON agent_interactions(project_id, session_id);

ALTER TABLE agent_interactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_interactions FORCE ROW LEVEL SECURITY;
CREATE POLICY agent_interactions_project_isolation ON agent_interactions
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 5. Implementation Records (Claims by Coding Agents)
CREATE TABLE IF NOT EXISTS implementation_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    session_id UUID REFERENCES agent_sessions(id) ON DELETE SET NULL,
    agent_platform VARCHAR(50) NOT NULL,
    summary TEXT NOT NULL,
    modified_files JSONB NOT NULL DEFAULT '[]'::jsonb,
    unresolved_issues JSONB NOT NULL DEFAULT '[]'::jsonb,
    candidate_status VARCHAR(50) NOT NULL DEFAULT 'DONE_UNVERIFIED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_implementation_records_task ON implementation_records(project_id, task_id);

ALTER TABLE implementation_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE implementation_records FORCE ROW LEVEL SECURITY;
CREATE POLICY implementation_records_project_isolation ON implementation_records
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 6. Test Executions (Verification Evidence)
CREATE TABLE IF NOT EXISTS test_executions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    session_id UUID REFERENCES agent_sessions(id) ON DELETE SET NULL,
    test_type VARCHAR(50) NOT NULL,
    framework VARCHAR(50),
    passed BOOLEAN NOT NULL,
    total_tests INT NOT NULL,
    passed_tests INT NOT NULL,
    failed_tests INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    error_summary TEXT,
    log_artifact_id UUID REFERENCES artifacts(id) ON DELETE SET NULL,
    recorded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_test_executions_task ON test_executions(project_id, task_id, passed);

ALTER TABLE test_executions ENABLE ROW LEVEL SECURITY;
ALTER TABLE test_executions FORCE ROW LEVEL SECURITY;
CREATE POLICY test_executions_project_isolation ON test_executions
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 7. Debugging Records (Failure Tracking & Resolutions)
CREATE TABLE IF NOT EXISTS debugging_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID REFERENCES tasks(id) ON DELETE SET NULL,
    failure_type VARCHAR(50) NOT NULL,
    classification VARCHAR(50) NOT NULL,
    failed_deployment_id UUID REFERENCES deployments(id) ON DELETE SET NULL,
    resolved_deployment_id UUID REFERENCES deployments(id) ON DELETE SET NULL,
    root_cause_analysis TEXT,
    attempt_summary TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'INVESTIGATING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_debugging_records_task ON debugging_records(project_id, task_id);
CREATE INDEX IF NOT EXISTS idx_debugging_records_deploy ON debugging_records(project_id, failed_deployment_id);

ALTER TABLE debugging_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE debugging_records FORCE ROW LEVEL SECURITY;
CREATE POLICY debugging_records_project_isolation ON debugging_records
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 8. Task Links (Traceability to Decisions, Artifacts, Commits, Deployments)
CREATE TABLE IF NOT EXISTS task_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    link_type VARCHAR(50) NOT NULL,
    target_id VARCHAR(255) NOT NULL,
    target_label VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_task_links UNIQUE (project_id, task_id, link_type, target_id)
);

CREATE INDEX IF NOT EXISTS idx_task_links_task ON task_links(project_id, task_id, link_type);

ALTER TABLE task_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE task_links FORCE ROW LEVEL SECURITY;
CREATE POLICY task_links_project_isolation ON task_links
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 9. Action Requests (Approval Inbox for EXECUTE_APPROVED_ACTIONS)
CREATE TABLE IF NOT EXISTS action_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    agent_token_id UUID REFERENCES mcp_tokens(id) ON DELETE SET NULL,
    action_type VARCHAR(100) NOT NULL,
    parameters JSONB NOT NULL DEFAULT '{}'::jsonb,
    is_destructive BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ,
    review_notes TEXT,
    execution_result JSONB
);

CREATE INDEX IF NOT EXISTS idx_action_requests_project_status ON action_requests(project_id, status);

ALTER TABLE action_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE action_requests FORCE ROW LEVEL SECURITY;
CREATE POLICY action_requests_project_isolation ON action_requests
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 10. Code Symbols (Structural Code Intelligence)
CREATE TABLE IF NOT EXISTS code_symbols (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    file_path VARCHAR(1024) NOT NULL,
    language VARCHAR(50) NOT NULL,
    symbol_name VARCHAR(255) NOT NULL,
    symbol_type VARCHAR(50) NOT NULL,
    signature TEXT,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_code_symbols_project_file ON code_symbols(project_id, file_path);
CREATE INDEX IF NOT EXISTS idx_code_symbols_project_name ON code_symbols(project_id, symbol_name);

ALTER TABLE code_symbols ENABLE ROW LEVEL SECURITY;
ALTER TABLE code_symbols FORCE ROW LEVEL SECURITY;
CREATE POLICY code_symbols_project_isolation ON code_symbols
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 11. Code Dependencies (Impact Analysis Graph)
CREATE TABLE IF NOT EXISTS code_dependencies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_path VARCHAR(1024) NOT NULL,
    target_path VARCHAR(1024) NOT NULL,
    dependency_type VARCHAR(50) NOT NULL,
    confidence VARCHAR(20) NOT NULL DEFAULT 'HIGH',
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_code_deps_source ON code_dependencies(project_id, source_path);
CREATE INDEX IF NOT EXISTS idx_code_deps_target ON code_dependencies(project_id, target_path);

ALTER TABLE code_dependencies ENABLE ROW LEVEL SECURITY;
ALTER TABLE code_dependencies FORCE ROW LEVEL SECURITY;
CREATE POLICY code_dependencies_project_isolation ON code_dependencies
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- 12. Agent Handoffs (Multi-Agent Trail)
CREATE TABLE IF NOT EXISTS agent_handoffs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    from_actor_type VARCHAR(50) NOT NULL,
    from_actor_id VARCHAR(255) NOT NULL,
    to_actor_type VARCHAR(50) NOT NULL,
    to_actor_id VARCHAR(255) NOT NULL,
    context_package_id UUID,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_agent_handoffs_task ON agent_handoffs(project_id, task_id);

ALTER TABLE agent_handoffs ENABLE ROW LEVEL SECURITY;
ALTER TABLE agent_handoffs FORCE ROW LEVEL SECURITY;
CREATE POLICY agent_handoffs_project_isolation ON agent_handoffs
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));
