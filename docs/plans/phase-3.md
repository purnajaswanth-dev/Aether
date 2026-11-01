# Phase 3 Implementation Plan: Extraction, RAG and Context Orchestrator

**Phase**: 3 of 6  
**Status**: APPROVED & IN PROGRESS  
**Estimated Steps**: 9  

---

## 1. Objectives & Constitutional Rules

1. **Meaning Preservation**: The original artifact is immutable, authoritative evidence. Structured extraction makes it usable; retrieval summaries make it fast. Never rely on AI summaries alone.
2. **Durable Ingestion Pipeline**: When an artifact version is stored, trigger a 12-step pipeline (metadata, structure parsing, secret detection, chunking, embeddings, full-text index, knowledge extraction, validation, graph relationships, timeline event). Failures retry with backoff and dead-letter queue.
3. **Structure-Aware Chunking**: Language and syntax-aware parsers (Markdown, Java, TypeScript, SQL, OpenAPI, Docker, Logs, PlainText). No arbitrary character slicing.
4. **Secret Detection & Redaction**: Regex patterns + Shannon entropy. Flag original artifact, redact secret in chunks/embeddings/packages, record secret finding event. Never expose secret values.
5. **Pluggable Embeddings with pgvector**: `EmbeddingProvider` interface with deterministic local embedding provider for fast offline reproducibility and configurable remote API. Stored with model name, version, and dimension. pgvector HNSW index.
6. **Structured Extraction with Provenance & Meaning-Preservation Engine**:
   - Extract requirements, decisions, architecture components, API contracts, database design, constraints.
   - Store exact supporting quote, offsets, platform, extraction method, validation state (`VERIFIED`, `UNVERIFIED`, `REJECTED`).
   - Programmatic meaning-preservation validation (quote matches offsets, entities preserved, modal conditions retained, multi-requirement splitting). Failing validation fails closed as `UNVERIFIED`.
   - Rule 5: Extracted decisions can NEVER be `APPROVED`; strictly `PROPOSED`.
7. **Hybrid Retrieval**: Combine 5 methods (Semantic pgvector, Full-text `tsvector` + trigram, Structured SQL, Graph recursive CTE, Exact ID) with Reciprocal Rank Fusion (RRF) and re-ranking. Current approved material strictly outranks historical/superseded.
8. **Token-Budgeted Context Orchestrator**:
   - Classify request (`ARCHITECTURE_REVIEW`, `IMPLEMENTATION`, `DEBUGGING`, etc.).
   - Model profiles table for context limits and reserved tokens.
   - Build package with highest-value items within budget, plus index of omitted items for staged retrieval.
   - Persist packages in `context_packages` and `context_retrieval_logs`.
   - Expose MCP tools: `search_project_memory` (upgraded), `get_context`, `expand_chunk`.
9. **Evaluation Harness**: 60+ question-answer pairs test dataset reporting Recall@k, precision, and current vs superseded accuracy in `docs/eval/phase-3.md`.
10. **Frontend**: Memory page with hybrid search, extracted knowledge browser with evidence drawer, context package preview with token meter, ingestion status & dead-letter queue, secret findings panel.

---

## 2. Database Schema (Flyway Migration `V6__phase_3_rag_and_orchestrator.sql`)

1. Extensions:
   - `CREATE EXTENSION IF NOT EXISTS vector;`
   - `CREATE EXTENSION IF NOT EXISTS pg_trgm;`
2. Tables:
   - `ingestion_jobs`: `id`, `project_id`, `artifact_id`, `artifact_version`, `status`, `attempts`, `last_error`, `created_at`, `started_at`, `completed_at`.
   - `secret_findings`: `id`, `project_id`, `artifact_id`, `artifact_version`, `finding_type`, `redacted_snippet`, `line_number`, `start_offset`, `end_offset`, `entropy`, `created_at`.
   - `artifact_chunks`: `id`, `project_id`, `artifact_id`, `artifact_version`, `chunk_index`, `parent_section_path`, `chunk_type`, `content`, `content_hash`, `start_offset`, `end_offset`, `start_line`, `end_line`, `metadata JSONB`, `tsv tsvector GENERATED ALWAYS AS (to_tsvector('english', content)) STORED`.
   - `embeddings`: `id`, `project_id`, `chunk_id`, `model_name`, `model_version`, `dimension`, `embedding vector(384)`, `created_at`.
   - `extracted_facts`: `id`, `project_id`, `artifact_id`, `artifact_version`, `category`, `fact_key`, `fact_statement`, `exact_quote`, `start_offset`, `end_offset`, `source_platform`, `extraction_method`, `validation_state`, `validation_notes`, `approval_state`, `confidence_score`, `reviewed_by`, `reviewed_at`, `created_at`.
   - `dependency_edges`: `id`, `project_id`, `source_type`, `source_id`, `target_type`, `target_id`, `relation_type`, `metadata JSONB`, `created_at`.
   - `model_profiles`: `id VARCHAR(50)`, `context_window_tokens`, `reserved_response_tokens`, `reserved_system_tokens`, `max_context_budget_tokens`, `created_at`.
   - `context_packages`: `id`, `project_id`, `request_type`, `task_description`, `target_model`, `token_budget`, `estimated_tokens`, `package_payload JSONB`, `omitted_items JSONB`, `created_at`.
   - `context_retrieval_logs`: `id`, `package_id`, `project_id`, `retrieval_method`, `query_text`, `results_count`, `duration_ms`, `created_at`.
3. Row-Level Security:
   - Enable & force RLS on all 8 project-scoped tables.
   - Policies using `current_project_matches(project_id)`.
   - Grant privileges to non-superuser role `aether_app`.

---

## 3. Backend Architecture & Package Structure

1. **Ingestion Package (`com.context_platform.aether.ingestion`)**:
   - `IngestionJobService`: Orchestrates 12-step pipeline, handles retries, idempotency, dead-letter storage.
   - `SecretDetectorService`: Multi-pattern regex (AWS, GitHub, OpenAI, JWT, Private Keys) + Shannon entropy calculation (> 4.5 bits/char). Redacts in-memory strings.
   - `ArtifactChunker`: Interface implemented by `MarkdownChunker`, `JavaChunker`, `TypeScriptChunker`, `SqlChunker`, `OpenApiChunker`, `DockerChunker`, `LogChunker`, `PlainTextChunker`.
   - `ChunkerRegistry`: Routes artifact content by type.
2. **Embedding Package (`com.context_platform.aether.embedding`)**:
   - `EmbeddingProvider`: Interface (`embed`, `embedBatch`, `getModelName`, `getDimension`).
   - `DeterministicLocalEmbeddingProvider`: Fast 384-d semantic feature vector for local testability and offline durability.
   - `EmbeddingService`: Manages embedding generation, batching, re-embedding jobs.
3. **Extraction & Meaning-Preservation (`com.context_platform.aether.extraction`)**:
   - `FactExtractorService`: Extracts facts across categories with exact quote and character offsets.
   - `MeaningPreservationValidator`: Programmatic verification of offsets, entity conservation, modal preservation, and clause splitting. Fails closed as `UNVERIFIED`.
   - `FactRepository` & `FactManagementService`: Fact querying, human verification (`VERIFIED`, `REJECTED`).
4. **Retrieval Package (`com.context_platform.aether.retrieval`)**:
   - `SemanticSearchService`: Vector cosine distance query via pgvector.
   - `KeywordSearchService`: PostgreSQL `ts_rank` + `pg_trgm` similarity.
   - `StructuredQueryService`: SQL filters on approved decisions, versions, domains.
   - `GraphQueryService`: Recursive CTE on `dependency_edges`.
   - `HybridRetrievalEngine`: Reciprocal Rank Fusion (RRF) + approval & recency re-ranking.
5. **Context Orchestrator (`com.context_platform.aether.orchestrator`)**:
   - `RequestClassifier`: Classifies task (`ARCHITECTURE_REVIEW`, `IMPLEMENTATION`, `DEBUGGING`, etc.).
   - `TokenBudgetManager`: Looks up model profiles, reserves tokens, enforces budget limit.
   - `ContextOrchestratorService`: Assembles memory layers (L1-L5), filters, deduplicates, packages, calculates omitted index, wraps in untrusted boundaries, persists logs.
6. **MCP Tools Upgrades (`com.context_platform.aether.mcp`)**:
   - Upgrade `search_project_memory` to call `HybridRetrievalEngine`.
   - Add `get_context(task, targetModel?, budget?)`.
   - Add `expand_chunk(chunkId)`.

---

## 4. Frontend Implementation

1. **Enhanced Shared Memory Page (`SharedMemoryPage.tsx`)**:
   - Hybrid search input with debounce and filters (domain, type, approval state, platform).
   - Chunk card rendering with parent section path, line offsets, SHA-256 hash, state pill, and **Expand Chunk** toggle.
2. **Extracted Knowledge Browser (`KnowledgeBrowserPage.tsx` or Tab in Memory)**:
   - Filter by category (Requirements, Decisions, Architecture, API, Constraints).
   - Validation state badges (`VERIFIED`, `UNVERIFIED`, `REJECTED`).
   - Evidence Drawer displaying original artifact context with highlighted span.
   - Authenticated human review actions (Verify / Reject).
3. **Context Package Preview (`ContextPreviewPage.tsx` or Tab in Memory)**:
   - Form: select Agent platform, target model, enter task description.
   - Visual token budget progress meter (used vs reserved vs remaining).
   - Tabs: Structured Context Package (untrusted boundary view), Included Items, Omitted Items Index, Retrieval Trace Log.
4. **Ingestion & Secrets Dashboard**:
   - Status per artifact (`INDEXED`, `PROCESSING`, `QUEUED`, `NEEDS_ATTENTION`).
   - Dead-letter queue view with **Retry Ingestion** button.
   - Secret findings alert panel showing redacted patterns and affected files.

---

## 5. Verification & Testing Strategy

1. **Unit & Integration Tests (`backend/src/test/java/com/context_platform/aether/`)**:
   - `ChunkerTestSuite`: Test all 8 chunkers.
   - `MeaningPreservationTest`: Test quotes, entity preservation, and the exact PostgreSQL sentence requirement.
   - `SecretDetectionTest`: Test AWS keys, OpenAI keys, JWT tokens, entropy threshold, and redaction.
   - `HybridRetrievalTest`: Semantic + keyword + structured + graph RRF scoring, approved over superseded priority.
   - `TokenBudgetOrchestratorTest`: Architecture review vs debugging package differentiation, token limits, omitted index.
   - `EvaluationHarnessTest`: 60-question benchmark with Recall@k, precision, and superseded decision rate.
   - `RlsRagIsolationTest`: Chunk, embedding, and fact tenant isolation.
2. **Frontend Tests**:
   - Component tests for Search, Evidence Drawer, Token Meter, and Secret Alert.
3. **Phase 3 Verification Report (`docs/phase-3-report.md`)**:
   - Documenting all gate requirements with concrete evidence.

