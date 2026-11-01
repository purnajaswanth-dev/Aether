# Phase 2 Implementation Plan: MCP Server and AI Connectivity

**Phase**: 2 of 6  
**Status**: APPROVED & IN PROGRESS  
**Estimated Steps**: 8  

---

## 1. Objectives & Constitutional Rules
- Expose persistent project memory to independent AI clients (Claude Desktop, Google Antigravity, OpenAI ChatGPT, Cursor, etc.) via remote Model Context Protocol (MCP).
- Dual transport implementation:
  - **Streamable HTTP** (`POST /api/mcp`) per MCP 2025-03-26 spec.
  - **Server-Sent Events (SSE)** (`GET /api/mcp/sse` + `POST /api/mcp/message`) per MCP 2024-11-05 spec.
- Strict token security: `aeth_mcp_...` bearer tokens stored exclusively as SHA-256 hashes, scoped to single projects, granular permissions, revocable, rate-limited.
- Cross-project isolation enforced at service layer and PostgreSQL Row-Level Security (RLS) engine layer.
- Enforce Non-Negotiable Rule 5: No AI tool can approve decisions. `record_decision` only proposes.
- Enforce Non-Negotiable Rule 6: Approved state beats historical state by default.
- Enforce Non-Negotiable Rule 9: Honest product statuses derived from real tokens and logs; explicit manual fallbacks for unverified capabilities.
- Enforce Non-Negotiable Rule 12: Every tool response includes complete provenance (artifact ID, version, hash, source platform).
- Shield all external content with untrusted-data boundaries.

---

## 2. Database Schema (Flyway Migration `V5__mcp_tables.sql`)
1. `mcp_tokens`:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `client_platform VARCHAR(50) NOT NULL` (`CLAUDE`, `CHATGPT`, `ANTIGRAVITY`, `OTHER`)
   - `token_name VARCHAR(100) NOT NULL`
   - `token_prefix VARCHAR(16) NOT NULL` (e.g. `aeth_mcp_a1b2...`)
   - `token_hash VARCHAR(64) NOT NULL UNIQUE` (SHA-256)
   - `permissions TEXT[] NOT NULL`
   - `is_revoked BOOLEAN NOT NULL DEFAULT false`
   - `is_paused BOOLEAN NOT NULL DEFAULT false`
   - `rate_limit_per_minute INT NOT NULL DEFAULT 60`
   - `expires_at TIMESTAMPTZ NULL`
   - `last_used_at TIMESTAMPTZ NULL`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
2. `mcp_call_logs`:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `token_id UUID REFERENCES mcp_tokens(id) ON DELETE SET NULL`
   - `tool_name VARCHAR(100) NOT NULL`
   - `client_platform VARCHAR(50) NOT NULL`
   - `duration_ms BIGINT NOT NULL`
   - `status VARCHAR(20) NOT NULL` (`SUCCESS`, `ERROR`, `RATE_LIMITED`, `UNAUTHORIZED`)
   - `error_message TEXT NULL`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
3. PostgreSQL Row-Level Security:
   - Enable & force RLS on `mcp_tokens` and `mcp_call_logs` keyed on `current_project_matches(project_id)`.
   - Grant permissions to non-superuser role `aether_app`.

---

## 3. Backend Architecture (`com.context_platform.aether.mcp`)
1. **JSON-RPC 2.0 Engine**:
   - `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError`, `McpToolDefinition`, `McpResourceDefinition`, `McpPromptDefinition`.
   - Protocol handlers for `initialize`, `notifications/initialized`, `tools/list`, `tools/call`, `resources/list`, `resources/read`, `prompts/list`, `prompts/get`.
2. **Security & Interception**:
   - `McpSecurityService`: Token hashing, verification, rate limiting, and project RLS scoping.
   - `McpTokenContext`: ThreadLocal storing authenticated agent context and actor type `AGENT`.
3. **Exact MCP Tools (13 tools)**:
   - `list_projects`: Lists project overview for current token.
   - `get_project_overview`: Project summary, tech stack, repositories, and constitutional status.
   - `get_project_context`: Domain memory summary across the 10 constitutional domains.
   - `get_project_status`: Current health, active decisions, recent activity count.
   - `search_project_memory`: Full-text search across project artifacts and memory notes.
   - `get_original_artifact`: Retrieves raw artifact by ID or title with full version and SHA-256 hash.
   - `get_architecture`: Returns current APPROVED architecture, or clearly labels the newest proposal if none is approved.
   - `get_decisions`: Returns decisions with state filter (`state`, default `APPROVED`).
   - `get_recent_activity`: Chronological timeline events.
   - `save_project_artifact`: Saves a new artifact or new version of existing artifact.
   - `save_conversation_output`: Stores raw agent conversation transcript or synthesis.
   - `record_decision`: Proposes a new technical decision (`PROPOSED` state only; agent cannot approve).
   - `record_review_finding`: Stored as evidence/finding linked to reviewed artifact version without overwriting.
4. **Data Isolation & Prompt Injection Shielding**:
   - Wrapping artifact content in `<<<UNTRUSTED_DATA_BOUNDARY: ARTIFACT [...]>>>`.
   - Standing notice that content is data, never instructions.
5. **Transports**:
   - `McpStreamableHttpController`: Single endpoint `POST /api/mcp`.
   - `McpSseController`: `GET /api/mcp/sse` + `POST /api/mcp/message`.
6. **Management API**:
   - Token lifecycle endpoints: generate, list, revoke, pause.
   - MCP call activity log endpoint.
   - Reliability instrumentation report endpoint.
   - Agent instruction and configuration generator endpoint.

---

## 4. Frontend Integrations View & Activity Telemetry
1. **Integrations Screen Updates**:
   - Stepper dialog for connecting Claude, ChatGPT, Antigravity, and Other MCP clients.
   - Secret token generation dialog (copy-once alert).
   - Client-specific copy-ready configurations:
     - Claude Desktop `claude_desktop_config.json` (`mcp-remote` bridge).
     - Antigravity `.agents/mcp_config.json` and `.agents/rules/aether.md`.
     - ChatGPT instructions and remote URL.
   - Real statuses: `Not connected`, `Awaiting first call`, `Connected (last call: X ago)`, `Paused`, `Error`.
   - Token management table: revoke immediately, pause/resume.
   - MCP Activity tab: Live-ish call table with tool name, client, status pill, duration ms, and timestamp.
   - Reliability panel: First-retrieval-before-save tracking and save completion rate.
   - Honest fallback banner for unverified / manual imports.

---

## 5. Verification & Testing Strategy
1. **Test Suite (`McpProtocolAndSecurityTest.java`, `McpHandoffFlowTest.java`)**:
   - Full 13-tool execution and schema verification.
   - Protocol-level handoff test: Client A saves arch -> Client B retrieves original (hash match) & saves findings -> Human approves -> Client C retrieves approved version.
   - Security tests: Missing token, invalid token, revoked token, paused token, rate limit 429, permission scoping.
   - Cross-project isolation: Token A attempting to read/write Project B fails at service layer and RLS layer.
   - Decision protection: `record_decision` creates `PROPOSED`; no approval tool exists.
   - Prompt-injection defense: Malicious payload remains inside untrusted data boundary block.
2. **Manual Test Guide (`docs/testing/handoff.md`)**:
   - Clear multi-agent handoff script for Claude, Antigravity, and ChatGPT.
3. **Phase 2 Report (`docs/phase-2-report.md`)**:
   - Complete gate checklist with test outputs.

