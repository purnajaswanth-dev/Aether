# Phase 6: Production Hardening & Deployment Implementation Plan

## Goal
Transform Aether from a feature-complete engineering context platform into a secure, reliable, observable, and deployable production system, adhering strictly to constitutional constraints, modular monolith boundaries, and evidence-driven verification.

## Workstream Breakdown & Deliverables

### Workstream 1: Security
1. **Threat Model (`docs/phase-6-threat-model.md`)**: STRIDE analysis covering Web App, REST APIs, MCP (SSE/Streamable HTTP), Integrations, Workstation Agent, Artifact Storage, and AI-Generated Content.
2. **Cross-Project RLS Penetration Test (`AdversarialCrossProjectSecurityTest.java`)**: Verify PostgreSQL RLS prevents data leaks under concurrent spoofed project sessions across artifacts, decisions, timeline events, and action requests.
3. **MCP Token Security Test (`McpTokenHardeningTest.java`)**: Verify expiration, instant revocation, rotation, and permission boundary enforcement for all 28 MCP tools.
4. **Prompt Injection & Untrusted Content Hardening (`PromptInjectionDefenseTest.java`)**: Verify system instructions cannot be overridden by untrusted inputs (log snippets, PR descriptions, malicious artifacts, fake JSON-RPC payloads).
5. **Security Report (`docs/phase-6-security-report.md`)**: Synthesize findings, automated test outputs, and vulnerability mitigations.

### Workstream 2: Reliability
1. **Durable Job & Restart Resilience Test (`ReliabilityAndJobRecoveryTest.java`)**: Ingestion idempotency under simulated JVM crashes, dead-letter queue (DLQ) retry mechanisms, and checkpoint reconciliation.
2. **Render Action Recovery (`RenderActionRecoveryTest.java`)**: Verify execution state consistency if network drops after provider dispatch.
3. **Reliability Report (`docs/phase-6-reliability-report.md`)**: Document SLOs, MTTR, RTO, RPO, and fault-tolerance verification.

### Workstream 3: Performance & Scalability
1. **Benchmarking Harness (`PerformanceBenchmarkTest.java`)**:
   - Hybrid retrieval latency (BM25 + ONNX embedding + status reranker).
   - Context package assembly with token budgeting.
   - EXPLAIN ANALYZE on critical database queries with index optimization where needed.
2. **Performance Report (`docs/phase-6-performance-report.md`)**: Document p50, p95, p99 latencies, throughput, and memory profiling.

### Workstream 4: Data Protection
1. **Data Export & Deletion Service (`DataManagementService.java` & `DataManagementController.java`)**: Project context export (JSON archive) and GDPR/constitutional cascade project deletion.
2. **Backup & Recovery Verification (`scripts/backup_restore.sh` / `.ps1`)**: Database and storage backup integrity verification.
3. **Data Protection Report (`docs/phase-6-data-protection-report.md`)**: Retention policies, cryptographic erasure, and disaster recovery procedures.

### Workstream 5: Observability
1. **Correlation ID & Tracing Filter (`CorrelationIdFilter.java`)**: Propagate `X-Correlation-ID` across HTTP requests, MCP sessions, and background threads via MDC.
2. **Custom Micrometer Metrics (`AetherMetrics.java`)**: Expose ingestion rates, retrieval latencies, MCP tool invocations, DLQ size, and integration polling health to `/actuator/prometheus`.
3. **Structured JSON Logging Configuration (`logback-spring.xml`)**: Clean production log formatting without secret leakage.

### Workstream 6: Frontend Quality
1. **Security & Accessibility UX Improvements**:
   - Destructive action confirmation dialogs with parameter inspections in `ApprovalInboxPage.tsx`.
   - WCAG AA contrast check and keyboard accessibility across views.
   - Global network error banner and session expiration interceptor in `api.ts`.
2. **Frontend Test Suite Expansion**: Expand component tests and verify production bundle.

### Workstream 7: Deployment
1. **Multi-stage Production Dockerfiles**:
   - `backend/Dockerfile` (Eclipse Temurin 17 JRE, non-root user).
   - `frontend/Dockerfile` & `nginx.conf` (SPA routing, TLS termination, Security Headers: CSP, HSTS, X-Frame-Options).
2. **Production Orchestration (`docker-compose.prod.yml`)**: PostgreSQL 16 + pgvector, backend, frontend, volume persistence, health checks, and secrets configuration.
3. **Deployment Guide (`docs/phase-6-deployment-guide.md`)**: Comprehensive production runbook.

### Workstream 8: Final Acceptance
1. **End-to-End Acceptance Scenario (`Phase6ProductionAcceptanceTest.java`)**: Verify complete 14-step engineering context flow.
2. **Final Report (`docs/phase-6-final-report.md`)**: Comprehensive acceptance results and production readiness verdict.

