-- ==============================================================================
-- Aether Post-Migration Verification Script for Neon PostgreSQL
-- Run this script against the Neon database to verify schema, extensions,
-- record counts, relationships, and vector/trigram query functionality.
-- ==============================================================================

\echo '-------------------------------------------------------------------------'
\echo ' 1. EXTENSIONS VERIFICATION'
\echo '-------------------------------------------------------------------------'

SELECT extname, extversion, extnamespace::regnamespace AS schema
FROM pg_extension
WHERE extname IN ('uuid-ossp', 'vector', 'pg_trgm')
ORDER BY extname;

\echo '-------------------------------------------------------------------------'
\echo ' 2. FLYWAY MIGRATION STATUS'
\echo '-------------------------------------------------------------------------'

SELECT version, description, type, script, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;

\echo '-------------------------------------------------------------------------'
\echo ' 3. CORE TABLE ROW COUNTS'
\echo '-------------------------------------------------------------------------'

SELECT 'users' AS table_name, count(*) AS total_records FROM users
UNION ALL
SELECT 'projects', count(*) FROM projects
UNION ALL
SELECT 'project_members', count(*) FROM project_members
UNION ALL
SELECT 'artifacts', count(*) FROM artifacts
UNION ALL
SELECT 'artifact_versions', count(*) FROM artifact_versions
UNION ALL
SELECT 'technical_decisions', count(*) FROM technical_decisions
UNION ALL
SELECT 'decision_versions', count(*) FROM decision_versions
UNION ALL
SELECT 'timeline_events', count(*) FROM timeline_events
UNION ALL
SELECT 'tasks', count(*) FROM tasks
UNION ALL
SELECT 'agent_sessions', count(*) FROM agent_sessions
UNION ALL
SELECT 'artifact_chunks', count(*) FROM artifact_chunks
UNION ALL
SELECT 'embeddings', count(*) FROM embeddings
UNION ALL
SELECT 'extracted_facts', count(*) FROM extracted_facts
UNION ALL
SELECT 'dependency_edges', count(*) FROM dependency_edges
UNION ALL
SELECT 'action_requests', count(*) FROM action_requests
ORDER BY table_name;

\echo '-------------------------------------------------------------------------'
\echo ' 4. REFERENTIAL INTEGRITY & ORPHAN CHECKS (Should all return 0)'
\echo '-------------------------------------------------------------------------'

SELECT 'orphaned_artifact_versions' AS check_name, count(*) AS violations
FROM artifact_versions av
LEFT JOIN artifacts a ON av.artifact_id = a.id
WHERE a.id IS NULL
UNION ALL
SELECT 'orphaned_artifact_chunks', count(*)
FROM artifact_chunks ac
LEFT JOIN artifacts a ON ac.artifact_id = a.id
WHERE a.id IS NULL
UNION ALL
SELECT 'orphaned_embeddings', count(*)
FROM embeddings e
LEFT JOIN artifact_chunks ac ON e.chunk_id = ac.id
WHERE ac.id IS NULL
UNION ALL
SELECT 'orphaned_decision_versions', count(*)
FROM decision_versions dv
LEFT JOIN technical_decisions td ON dv.decision_id = td.id
WHERE td.id IS NULL;

\echo '-------------------------------------------------------------------------'
\echo ' 5. FUNCTIONAL EXTENSION TESTS'
\echo '-------------------------------------------------------------------------'

-- Test Full-Text Search (tsvector / tsquery)
SELECT 
    'PostgreSQL Full-Text Search' AS feature,
    CASE WHEN to_tsvector('english', 'Aether unified engineering platform') @@ to_tsquery('english', 'engineering')
         THEN 'PASSED' ELSE 'FAILED' END AS test_status;

-- Test pg_trgm (Trigram Similarity)
SELECT 
    'pg_trgm Trigram Similarity' AS feature,
    CASE WHEN similarity('Aether Context', 'Aether Engineering Context') > 0.4
         THEN 'PASSED' ELSE 'FAILED' END AS test_status;

-- Test pgvector (Vector operations and dimensions)
SELECT 
    'pgvector Cosine Distance (<=>)' AS feature,
    CASE WHEN ('[0.1, 0.2, 0.3]'::vector <=> '[0.1, 0.2, 0.3]'::vector) = 0.0
         THEN 'PASSED' ELSE 'FAILED' END AS test_status;

\echo '-------------------------------------------------------------------------'
\echo ' Verification script completed.'
\echo '-------------------------------------------------------------------------'
