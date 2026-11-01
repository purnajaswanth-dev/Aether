-- V6: Phase 3 RAG, Extraction, Secret Detection, Embeddings, and Context Orchestrator

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Add has_secrets column to artifacts if not exists
ALTER TABLE artifacts ADD COLUMN IF NOT EXISTS has_secrets BOOLEAN NOT NULL DEFAULT false;

-- 1. Ingestion Jobs (Durable, Idempotent, Resumable)
CREATE TABLE IF NOT EXISTS ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    artifact_version INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED', -- QUEUED, PROCESSING, INDEXED, NEEDS_ATTENTION, FAILED
    attempts INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 3,
    last_error TEXT NULL,
    step_details JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL
);

CREATE INDEX idx_ingestion_jobs_project ON ingestion_jobs(project_id, created_at DESC);
CREATE INDEX idx_ingestion_jobs_artifact ON ingestion_jobs(artifact_id, artifact_version);
CREATE INDEX idx_ingestion_jobs_status ON ingestion_jobs(status);

-- 2. Secret Findings
CREATE TABLE IF NOT EXISTS secret_findings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    artifact_version INT NOT NULL,
    finding_type VARCHAR(50) NOT NULL, -- AWS_KEY, GITHUB_TOKEN, OPENAI_KEY, JWT_TOKEN, PRIVATE_KEY, HIGH_ENTROPY
    redacted_snippet TEXT NOT NULL,
    line_number INT NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    entropy DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_secret_findings_project ON secret_findings(project_id, created_at DESC);
CREATE INDEX idx_secret_findings_artifact ON secret_findings(artifact_id, artifact_version);

-- 3. Structure-Aware Artifact Chunks
CREATE TABLE IF NOT EXISTS artifact_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    artifact_version INT NOT NULL,
    chunk_index INT NOT NULL,
    parent_section_path TEXT NOT NULL,
    chunk_type VARCHAR(50) NOT NULL, -- MARKDOWN_SECTION, JAVA_CLASS, JAVA_METHOD, TYPESCRIPT_DECL, SQL_STATEMENT, OPENAPI_OPERATION, DOCKER_STAGE, LOG_ENTRY, PLAIN_PARAGRAPH
    content TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    start_line INT NOT NULL,
    end_line INT NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_artifact_chunks_project ON artifact_chunks(project_id);
CREATE INDEX idx_artifact_chunks_artifact ON artifact_chunks(artifact_id, artifact_version);
CREATE INDEX idx_artifact_chunks_tsv ON artifact_chunks USING GIN(tsv);
CREATE INDEX idx_artifact_chunks_trgm ON artifact_chunks USING GIN(content gin_trgm_ops);

-- 4. Vector Embeddings
CREATE TABLE IF NOT EXISTS embeddings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES artifact_chunks(id) ON DELETE CASCADE,
    model_name VARCHAR(100) NOT NULL,
    model_version VARCHAR(50) NOT NULL,
    dimension INT NOT NULL,
    embedding vector(384) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_embeddings_project ON embeddings(project_id);
CREATE INDEX idx_embeddings_chunk ON embeddings(chunk_id);
CREATE INDEX idx_embeddings_hnsw ON embeddings USING hnsw (embedding vector_cosine_ops);

-- 5. Extracted Facts with Provenance and Meaning Preservation
CREATE TABLE IF NOT EXISTS extracted_facts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    artifact_id UUID NOT NULL REFERENCES artifacts(id) ON DELETE CASCADE,
    artifact_version INT NOT NULL,
    category VARCHAR(50) NOT NULL, -- REQUIREMENT, DECISION, ARCHITECTURE_COMPONENT, API_CONTRACT, DATABASE_DESIGN, CONSTRAINT
    fact_key VARCHAR(255) NOT NULL,
    fact_statement TEXT NOT NULL,
    exact_quote TEXT NOT NULL,
    start_offset INT NOT NULL,
    end_offset INT NOT NULL,
    source_platform VARCHAR(50) NOT NULL,
    extraction_method VARCHAR(100) NOT NULL,
    validation_state VARCHAR(30) NOT NULL, -- VERIFIED, UNVERIFIED, REJECTED
    validation_notes TEXT NULL,
    approval_state VARCHAR(30) NOT NULL DEFAULT 'PROPOSED', -- PROPOSED, APPROVED, REJECTED
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 1.0,
    reviewed_by UUID REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_extracted_facts_project ON extracted_facts(project_id, category);
CREATE INDEX idx_extracted_facts_artifact ON extracted_facts(artifact_id, artifact_version);
CREATE INDEX idx_extracted_facts_validation ON extracted_facts(project_id, validation_state);

-- 6. Dependency Edges (Architecture & Component Knowledge Graph)
CREATE TABLE IF NOT EXISTS dependency_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    source_type VARCHAR(50) NOT NULL,
    source_id UUID NOT NULL,
    source_name VARCHAR(255) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    target_name VARCHAR(255) NOT NULL,
    relation_type VARCHAR(50) NOT NULL, -- DEPENDS_ON, IMPLEMENTS, CONSTRAINED_BY, RELATES_TO
    metadata JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dependency_edges_project ON dependency_edges(project_id);
CREATE INDEX idx_dependency_edges_source ON dependency_edges(source_id);
CREATE INDEX idx_dependency_edges_target ON dependency_edges(target_id);

-- 7. Model Profiles for Token Budgeting
CREATE TABLE IF NOT EXISTS model_profiles (
    model_id VARCHAR(50) PRIMARY KEY,
    context_window_tokens INT NOT NULL,
    reserved_response_tokens INT NOT NULL,
    reserved_system_tokens INT NOT NULL,
    max_context_budget_tokens INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO model_profiles (model_id, context_window_tokens, reserved_response_tokens, reserved_system_tokens, max_context_budget_tokens)
VALUES
    ('claude-3-5-sonnet', 200000, 8192, 4096, 64000),
    ('gpt-4o', 128000, 4096, 4096, 48000),
    ('gemini-1.5-pro', 1000000, 8192, 4096, 128000),
    ('default', 32000, 4096, 2048, 16000)
ON CONFLICT (model_id) DO NOTHING;

-- 8. Context Packages (Synthesized & Version-Verified)
CREATE TABLE IF NOT EXISTS context_packages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    request_type VARCHAR(50) NOT NULL, -- ARCHITECTURE_REVIEW, IMPLEMENTATION, DEBUGGING, TESTING, DEPLOYMENT, GENERAL
    task_description TEXT NOT NULL,
    target_model VARCHAR(50) NOT NULL,
    token_budget INT NOT NULL,
    estimated_tokens INT NOT NULL,
    package_payload JSONB NOT NULL,
    omitted_items JSONB NOT NULL DEFAULT '[]',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_context_packages_project ON context_packages(project_id, created_at DESC);

-- 9. Context Retrieval Logs
CREATE TABLE IF NOT EXISTS context_retrieval_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    package_id UUID REFERENCES context_packages(id) ON DELETE CASCADE,
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    retrieval_method VARCHAR(50) NOT NULL, -- SEMANTIC, KEYWORD, STRUCTURED_SQL, GRAPH, EXACT
    query_text TEXT NOT NULL,
    results_count INT NOT NULL,
    duration_ms BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_context_retrieval_logs_pkg ON context_retrieval_logs(package_id);
CREATE INDEX idx_context_retrieval_logs_proj ON context_retrieval_logs(project_id, created_at DESC);

-- =========================================================================
-- PostgreSQL Row-Level Security (RLS) Policies on all 8 project-scoped tables
-- =========================================================================

ALTER TABLE ingestion_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE ingestion_jobs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS ingestion_jobs_isolation_policy ON ingestion_jobs;
CREATE POLICY ingestion_jobs_isolation_policy ON ingestion_jobs
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE secret_findings ENABLE ROW LEVEL SECURITY;
ALTER TABLE secret_findings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS secret_findings_isolation_policy ON secret_findings;
CREATE POLICY secret_findings_isolation_policy ON secret_findings
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE artifact_chunks ENABLE ROW LEVEL SECURITY;
ALTER TABLE artifact_chunks FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS artifact_chunks_isolation_policy ON artifact_chunks;
CREATE POLICY artifact_chunks_isolation_policy ON artifact_chunks
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE embeddings FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS embeddings_isolation_policy ON embeddings;
CREATE POLICY embeddings_isolation_policy ON embeddings
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE extracted_facts ENABLE ROW LEVEL SECURITY;
ALTER TABLE extracted_facts FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS extracted_facts_isolation_policy ON extracted_facts;
CREATE POLICY extracted_facts_isolation_policy ON extracted_facts
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE dependency_edges ENABLE ROW LEVEL SECURITY;
ALTER TABLE dependency_edges FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS dependency_edges_isolation_policy ON dependency_edges;
CREATE POLICY dependency_edges_isolation_policy ON dependency_edges
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE context_packages ENABLE ROW LEVEL SECURITY;
ALTER TABLE context_packages FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS context_packages_isolation_policy ON context_packages;
CREATE POLICY context_packages_isolation_policy ON context_packages
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

ALTER TABLE context_retrieval_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE context_retrieval_logs FORCE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS context_retrieval_logs_isolation_policy ON context_retrieval_logs;
CREATE POLICY context_retrieval_logs_isolation_policy ON context_retrieval_logs
    FOR ALL
    USING (current_project_matches(project_id))
    WITH CHECK (current_project_matches(project_id));

-- Grant privileges to non-superuser tenant role if it exists
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'aether_app') THEN
        GRANT ALL PRIVILEGES ON ingestion_jobs TO aether_app;
        GRANT ALL PRIVILEGES ON secret_findings TO aether_app;
        GRANT ALL PRIVILEGES ON artifact_chunks TO aether_app;
        GRANT ALL PRIVILEGES ON embeddings TO aether_app;
        GRANT ALL PRIVILEGES ON extracted_facts TO aether_app;
        GRANT ALL PRIVILEGES ON dependency_edges TO aether_app;
        GRANT ALL PRIVILEGES ON model_profiles TO aether_app;
        GRANT ALL PRIVILEGES ON context_packages TO aether_app;
        GRANT ALL PRIVILEGES ON context_retrieval_logs TO aether_app;
    END IF;
END;
$$;

