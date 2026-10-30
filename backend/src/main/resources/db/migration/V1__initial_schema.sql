-- V1: Initial Schema for Unified Engineering Context Platform (Aether)

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_email ON users(email);

-- Projects
CREATE TABLE projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id VARCHAR(32) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    project_type VARCHAR(64) NOT NULL,
    tech_stack JSONB NOT NULL DEFAULT '[]'::jsonb,
    directory VARCHAR(1024),
    storage_preference VARCHAR(32) NOT NULL DEFAULT 'LOCAL_FIRST',
    archived BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_projects_public_id ON projects(public_id);
CREATE INDEX idx_projects_created_at ON projects(created_at);

-- Project Members
CREATE TABLE project_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(32) NOT NULL DEFAULT 'MEMBER',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_members UNIQUE(project_id, user_id)
);

CREATE INDEX idx_project_members_user ON project_members(user_id);
CREATE INDEX idx_project_members_project ON project_members(project_id);

-- Project Integrations
CREATE TABLE project_integrations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    provider_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'NOT_CONNECTED',
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_integrations UNIQUE(project_id, provider_id)
);

CREATE INDEX idx_project_integrations_project ON project_integrations(project_id);

-- Project Permissions (6 core permissions per integration per project)
CREATE TABLE project_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    integration_id UUID REFERENCES project_integrations(id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    granted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_permissions UNIQUE(project_id, integration_id, permission)
);

CREATE INDEX idx_project_permissions_proj_perm ON project_permissions(project_id, permission);

-- Artifacts
CREATE TABLE artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    source_platform VARCHAR(64) NOT NULL,
    source_reference VARCHAR(512),
    artifact_type VARCHAR(64) NOT NULL,
    related_task_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_artifacts_project_id ON artifacts(project_id);
CREATE INDEX idx_artifacts_type ON artifacts(project_id, artifact_type);
CREATE INDEX idx_artifacts_platform ON artifacts(project_id, source_platform);

-- Artifact Versions (Append-only)
CREATE TABLE artifact_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    content TEXT,
    storage_key VARCHAR(512),
    content_hash VARCHAR(64) NOT NULL,
    byte_size BIGINT NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN_USER',
    change_summary TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_artifact_versions UNIQUE(artifact_id, version)
);

CREATE INDEX idx_artifact_versions_artifact ON artifact_versions(artifact_id);
CREATE INDEX idx_artifact_versions_project ON artifact_versions(project_id);
CREATE INDEX idx_artifact_versions_hash ON artifact_versions(content_hash);

-- Artifact References
CREATE TABLE artifact_references (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    target_artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    reference_type VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_artifact_references_source ON artifact_references(source_artifact_id);
CREATE INDEX idx_artifact_references_target ON artifact_references(target_artifact_id);

-- Technical Decisions
CREATE TABLE technical_decisions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    public_id VARCHAR(32) NOT NULL,
    title VARCHAR(255) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'PROPOSED',
    category VARCHAR(64) NOT NULL,
    current_version INTEGER NOT NULL DEFAULT 1,
    lock_version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_project_decision_public_id UNIQUE(project_id, public_id)
);

CREATE INDEX idx_decisions_project ON technical_decisions(project_id);
CREATE INDEX idx_decisions_state ON technical_decisions(project_id, state);
CREATE INDEX idx_decisions_category ON technical_decisions(project_id, category);

-- Decision Versions
CREATE TABLE decision_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id UUID NOT NULL REFERENCES technical_decisions(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    state VARCHAR(32) NOT NULL,
    problem_statement TEXT NOT NULL,
    proposed_solution TEXT NOT NULL,
    rationale TEXT,
    consequences TEXT,
    source_artifact_version_id UUID REFERENCES artifact_versions(id),
    actor_id VARCHAR(255) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN_USER',
    review_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_decision_versions UNIQUE(decision_id, version)
);

CREATE INDEX idx_decision_versions_decision ON decision_versions(decision_id);
CREATE INDEX idx_decision_versions_project ON decision_versions(project_id);

-- Decision Evidence
CREATE TABLE decision_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id UUID NOT NULL REFERENCES technical_decisions(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    evidence_type VARCHAR(64) NOT NULL,
    description TEXT NOT NULL,
    artifact_version_id UUID REFERENCES artifact_versions(id),
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_decision_evidence_decision ON decision_evidence(decision_id);

-- Timeline Events (Append-only)
CREATE TABLE timeline_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    task_id UUID,
    source_platform VARCHAR(64) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type VARCHAR(64) NOT NULL,
    associated_artifact_id UUID REFERENCES artifacts(id),
    associated_decision_id UUID REFERENCES technical_decisions(id),
    decision_version INTEGER,
    status VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    actor_id VARCHAR(255) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'HUMAN_USER',
    summary TEXT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_timeline_events_project ON timeline_events(project_id, timestamp DESC);
CREATE INDEX idx_timeline_events_type ON timeline_events(project_id, event_type);

-- Audit Events
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id) ON DELETE SET NULL,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL,
    ip_address VARCHAR(64),
    user_agent VARCHAR(255),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_events_user ON audit_events(user_id, created_at DESC);
CREATE INDEX idx_audit_events_project ON audit_events(project_id, created_at DESC);

