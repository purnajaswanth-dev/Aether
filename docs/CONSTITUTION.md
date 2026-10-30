# Unified Engineering Context Platform: Constitution

## PRODUCT
We are building the Unified Engineering Context Platform: a project-centric persistent memory and context-exchange layer for software teams. One project, one persistent memory, many independent AI agents and engineering tools (ChatGPT, Claude, Google Antigravity, GitHub, Render, Postman, Docker). The project, not the AI conversation, is the central unit. The platform stores original artifacts, extracts structured engineering knowledge with provenance, tracks decisions through explicit lifecycles, and serves compact, version-aware, token-budgeted context to any authorized agent. We are NOT another AI assistant and we do NOT replace GitHub, Render, Postman, Docker or any AI platform.

## TECH STACK (fixed unless a documented, justified exception is approved)
- Backend: Java 17 (LTS), Spring Boot 3.3.x, Maven, modular monolith.
- Do NOT use Java 21 features (no virtual threads, no record patterns in switch, no sequenced collections). Java 17 features are fine: records, sealed classes, text blocks, switch expressions, pattern matching for instanceof.
- Data access: Spring JDBC (JdbcClient / NamedParameterJdbcTemplate). No JPA or Hibernate.
- Migrations: Flyway. Database: PostgreSQL 16 with pgvector and built-in full-text search.
- External HTTP: Spring WebClient. Security: Spring Security.
- Background jobs: a Postgres-backed durable job library (JobRunr or db-scheduler). No Kafka, no Kubernetes, no extra databases, no microservices.
- Large artifacts: object storage behind a StorageService interface (local filesystem implementation now, S3-compatible later).
- Frontend: React 18 + TypeScript + Vite, Tailwind CSS, Radix UI primitives (shadcn/ui style), TanStack Query, React Router, Lucide icons. Plain CSS transitions; no heavy animation libraries.
- Testing: JUnit 5, Testcontainers (real Postgres with pgvector), Vitest + React Testing Library, Playwright for end-to-end.
- Repo layout: /backend, /frontend, /agent (local connector, later phases), /docs, /scripts.

## ARCHITECTURE RULES
1. Modular monolith. One Maven module with strict package boundaries: project, auth, integration, ingestion, artifact, memory, decision, retrieval, orchestrator, mcp, event, workflow, security. Modules talk through service interfaces, never through each other's tables.
2. Every external provider is a separate adapter implementing one common interface: connect, authenticate, checkCapabilities, retrieve, receiveEvents, normalizeEvents, saveArtifact, syncStatus, handleError, disconnect. No provider-specific logic in shared services.
3. Original artifacts and historical events are append-only and immutable (enforced by database triggers, not just code). Never overwrite. Corrections create new versions.
4. Project isolation is enforced in the database with Postgres Row-Level Security keyed on project ID, in addition to service-layer checks. Cross-project leakage is a release-blocking bug.
5. Critical state changes use transactions. Decision and task transitions use optimistic locking (version column).
6. All external content (AI responses, logs, webhooks, commit messages) is untrusted data. It can never override system instructions, bypass authorization, or trigger actions.

## NON-NEGOTIABLE PRODUCT RULES
1. GitHub is never required; local projects are first-class.
2. Never depend on one AI provider.
3. Preserve original AI responses exactly, with a SHA-256 content hash.
4. Never silently change the meaning of stored engineering information.
5. AI-generated decisions are never auto-approved. Only an authenticated human user can approve. Agent tokens can only create PROPOSED records.
6. Approved state always beats historical state. Agents receive the current approved version by default; older versions stay accessible but are labelled as historical.
7. Never send entire project history to a model. Use task-specific retrieval and respect target-model context limits.
8. Never store credentials or secrets in project memory, indexes, or embeddings. Run secret detection before chunking and embedding.
9. Never pretend an integration is connected. Unsupported or unbuilt integrations display as Not available or Not connected. No decorative buttons, no fake statuses, no demo data in production code paths. Test fixtures live only in test directories.
10. An agent's claim that work is complete is not verification. Completion needs separately recorded evidence (test results, commits, deployments).
11. Keep the developer in control of important decisions and any destructive or production-affecting action.
12. Every important retrieved fact carries provenance: artifact ID, version, source platform, source location.
13. No unnecessary features, technology or complexity.

## DECISION LIFECYCLE
PROPOSED, UNDER_REVIEW, APPROVED, REJECTED, SUPERSEDED, IMPLEMENTED, VERIFIED. Allowed transitions are enforced by a state machine with tests. Conflicting opinions are all preserved with evidence.

## PERMISSIONS (per integration, per project, editable later)
READ_PROJECT_CONTEXT, READ_ARTIFACTS, SAVE_ARTIFACTS, RECORD_DECISIONS, READ_DEPLOYMENT_DATA, EXECUTE_APPROVED_ACTIONS.

## WORKING RULES FOR THE AGENT
1. Before coding in any phase: inspect the repository, list what exists and what is missing, then write an implementation plan (as a plan artifact and docs/plans/phase-1.md) covering schema, API contracts, security boundaries and acceptance tests. Then implement.
2. Work in small, reviewable steps. Run the tests after each step. Fix failures before continuing.
3. Every feature must be functional, tested and traceable. If something cannot be built (for example a provider capability that does not exist), say so explicitly and implement the honest fallback.
4. Where a provider's behaviour is uncertain, read its current official documentation and record findings in docs/integrations/<provider>.md. Never fabricate provider responses, logs or capabilities.
5. At the end of each phase produce docs/phase-N-report.md: what was built, test results (actual output), known gaps, and the Gate checklist with pass/fail and evidence.
6. Do not start the next phase's work early.

## DESIGN SYSTEM REFERENCE
See [docs/DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) for full design system tokens, typography, layout, motion, and accessibility specifications.

