# Phase 4 Implementation Plan: External Integrations and Local Agent

## Executive Summary
Phase 4 connects Aether to real-world engineering platforms (GitHub, Render, Postman, Docker) and directly to local developer workstations via a cross-platform Go agent (`/agent`). In accordance with constitutional principles:
- **Local First (Rule 1)**: GitHub and cloud platforms are never required; local projects are first-class citizens.
- **Provider Decoupling (Rule 2)**: Every provider is an isolated adapter implementing a common constitution interface; no provider-specific logic in core services.
- **Credential Protection (Rule 8)**: Tokens and secrets are encrypted at rest with AES-GCM envelope encryption and never logged, indexed, or returned via APIs.
- **Honest Capabilities (Rule 9)**: The UI presents verified provider capabilities (Webhooks vs Polling vs Manual vs Unsupported).
- **Untrusted External Data (Rule 6)**: Webhooks, commit messages, PR descriptions, and build logs are delimited as untrusted data (`UNTRUSTED_DATA_BOUNDARY`).

---

## 1. Research Findings & Capability Baseline
Verified against official provider specifications in `docs/integrations/`:
1. **GitHub (`github.md`)**: GitHub App / Fine-grained PAT; HMAC-SHA256 webhooks (`push`, `pull_request`, `workflow_run`); replay protection via `X-GitHub-Delivery`; 5,000 req/hr rate limits.
2. **Render (`render.md`)**: REST API v1 (`rnd_...`); service inventory and deployment polling; Render Deploy Hooks trigger builds but do **not** emit outbound webhooks, so status updates use cursor-based **POLLING**; failure detection retrieves build/runtime logs keyed strictly by deployment ID; 7-day (free) / 30-day (paid) retention limits.
3. **Postman (`postman.md`)**: REST API (`PMAK-...`); collections and OpenAPI schema import; test monitor results; environment secrets are **prohibited and refused** by constitutional rule.
4. **Docker (`docker.md`)**: Static parsing of `Dockerfile` and `docker-compose.yml`; local execution verification via `aether-agent run -- docker build`; direct daemon socket manipulation is **unsupported** for security and cross-platform integrity.

---

## 2. Schema Architecture (`V7__phase_4_integrations_and_agent.sql`)
1. `integration_configs`: Project-scoped configuration, health status, sync intervals.
2. `integration_credentials`: AES-256-GCM envelope-encrypted secrets, IV, auth tag, masked preview.
3. `processed_events`: Deduplication table with unique `(project_id, provider, event_id)` constraint.
4. `dead_letter_events`: Failed webhooks and sync errors with payload preservation and manual retry.
5. `integration_checkpoints`: Per-provider cursor/timestamp for outage recovery and reconciliation.
6. `deployments`: Render deployment tracking keyed by `external_deploy_id`, failure classification (`BUILD_FAILURE`, `DEPLOY_FAILURE`, `RUNTIME_FAILURE`), linked raw logs artifact, and extracted errors.
7. `agent_devices`: Device-code pairing state, revocable device token hashes (`aeth_dev_...`), heartbeat, and status.
8. `agent_file_metadata`: Local file metadata tree (relative path, SHA-256 hash, size, mtime, language, symbols).

All tables enforce PostgreSQL Row-Level Security (`current_project_matches(project_id)`).

---

## 3. Credential Security Layer
- `KeyProvider` interface with `EnvironmentKeyProvider` implementation using 256-bit AES master key (`AETHER_ENCRYPTION_MASTER_KEY`).
- `CredentialEncryptionService`:
  - Encryption: Generates 12-byte secure random IV, encrypts plaintext using `AES/GCM/NoPadding` (128-bit authentication tag).
  - Decryption: Decrypts only inside authorized adapter execution.
  - Masking: Exposes only safe prefix and suffix (e.g. `rnd_...9a12`, `ghp_...b4c1`).

---

## 4. Integration Adapter Framework & Reliability Layer
- `IntegrationAdapter` interface implementing the 10 constitutional operations:
  `connect`, `authenticate`, `checkCapabilities`, `retrieve`, `receiveEvents`, `normalizeEvents`, `saveArtifact`, `syncStatus`, `handleError`, `disconnect`.
- `IntegrationRegistry` managing adapters by `ProviderType`.
- `IntegrationReliabilityService`:
  - Constant-time HMAC-SHA256 signature verification (`MessageDigest.isEqual`).
  - Delivery deduplication via `processed_events`.
  - Rate-limit tracker honoring `x-ratelimit-remaining` and `x-ratelimit-reset`.
  - Checkpoint tracking and automatic backoff retry with jitter.
  - Dead-letter queue dispatch.

---

## 5. Provider Adapters
1. **GitHub Adapter (`GitHubAdapter.java`)**:
   - Webhook endpoint `POST /api/webhooks/github/{projectId}` with signature validation.
   - Normalizes commits, PRs, and CI workflow runs into `timeline_events`.
   - Stores raw payloads as immutable artifacts (`GITHUB_EVENT`).
   - Links task IDs conservatively when matching patterns appear.
2. **Render Adapter (`RenderAdapter.java`)**:
   - Discovers services via `GET /v1/services`.
   - Polls deployment statuses and detects failure states.
   - Retrieves build logs and runtime logs for the exact deployment window.
   - Stores raw logs as immutable artifact (`RENDER_BUILD_LOGS`).
   - Parses compiler/runtime errors and classifies failure (`BUILD_FAILURE`, `DEPLOY_FAILURE`, `RUNTIME_FAILURE`).
   - Records deployment entities in `deployments` table.
3. **Postman Adapter (`PostmanAdapter.java`)**:
   - Imports collections and OpenAPI schemas.
   - Refuses `GET /environments` to prevent secret leakage.
4. **Docker Adapter (`DockerAdapter.java`)**:
   - Parses Dockerfile and compose configurations.
   - Declares daemon sockets as unsupported.

---

## 6. Local Project Agent (`/agent` in Go)
- Single cross-platform binary written in Go 1.26:
  - `pair`: Device-code flow displaying user code (e.g. `AETH-7821`) and polling until approved.
  - `start`: Initiates file tree indexing and debounced filesystem watcher.
  - `pause`: Immediately suspends all sync operations.
  - `resume`: Resumes watching and triggers incremental reconciliation.
  - `status`: Displays current project, watched path, sync stats, last heartbeat.
  - `disconnect`: Revokes device token with backend and removes local credentials.
  - `run -- <command>`: Wraps test/build commands, captures stdout/stderr, exit code, duration, and uploads execution output as an artifact.
- Exclusion engine: strictly blocks `.env*`, `*.pem`, `*.key`, `id_rsa*`, credentials, `node_modules`, `target`, `.git`.
- Local-only mode (`--local-only`): 0 outbound network calls; serves local HTTP on `localhost:8124`.

---

## 7. MCP Tools Added Now
1. `get_latest_deployment`: Returns latest deployment details for a project/service.
2. `get_deployment_status`: Returns current deployment status and failure classification.
3. `get_deployment_logs`: Returns raw logs in untrusted-data boundary plus extracted errors. Requires `READ_DEPLOYMENT_DATA`.
4. `get_previous_failures`: Retrieves recent failure history with error summaries.
5. `get_relevant_files`: Returns file metadata and symbols indexed by the local agent.

---

## 8. Frontend Implementation
- **Integrations Console (`IntegrationsPage.tsx`)**:
  - Real connect modals for GitHub, Render, Postman, and Local Agent.
  - Capability badges (`WEBHOOK`, `POLLING`, `MANUAL`, `NOT_AVAILABLE`).
  - Real status badges (`CONNECTED`, `SYNCING`, `RATE_LIMITED`, `PAUSED`, `NOT_CONNECTED`, `NOT_AVAILABLE`).
  - Action buttons: Sync Now, Pause, Reconnect, Disconnect.
  - Dead-Letter Queue drawer with manual retry.
- **Deployments Console (`DeploymentsTab.tsx`)**:
  - Deployments table with status pills and failure badges.
  - Log drawer with virtualized, searchable log viewer, copy handle, linked commits/tasks.
- **Local Project Wizard (`LocalProjectWizard.tsx`)**:
  - Step-by-step pairing wizard, directory picker, permission configuration, live sync progress.

---

## 9. Verification & Acceptance Plan
- `WebhookSignatureAndSecurityTest`: Valid, invalid, replayed, and tampered signatures.
- `IntegrationReliabilityTest`: Deduplication, out-of-order event tolerance, DLQ routing.
- `CredentialEncryptionTest`: AES-GCM envelope encryption, key isolation, masked previews.
- `RenderDeploymentAndLogsTest`: Failure detection, log retrieval, error classification, deployment scoping.
- `LocalAgentSecurityTest`: Exclusion filtering (`.env`, `.key`), local-only mode zero-network guarantee.
- `McpDeploymentToolsTest`: Authorization checks (`READ_DEPLOYMENT_DATA`), log payloads in untrusted delimiters.

