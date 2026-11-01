# Phase 5 Implementation Plan: Workflow Coordination

**Platform**: Aether (Unified Engineering Context Platform)  
**Phase**: 5 — Workflow Coordination  
**Date**: October 31, 2026  
**Status**: IN_PLANNING  

---

## 1. Goal

Coordinate tasks, agent handoffs, implementation tracking, verification evidence, and debugging packages across all connected tools so that the human developer is no longer the manual integration layer between AI agents and developer platforms.

---

## 2. Architecture & Schema Design (`V8__phase_5_workflow_coordination.sql`)

### 2.1 Database Tables (All RLS-Forced via `current_project_matches(project_id)`)

1. **`tasks`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `task_number INT NOT NULL` (per-project auto-incrementing integer for display e.g. `AETH-12`)
   - `title VARCHAR(255) NOT NULL`
   - `description TEXT`
   - `status VARCHAR(50) NOT NULL DEFAULT 'BACKLOG'`
   - `priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM'` (`LOW`, `MEDIUM`, `HIGH`, `URGENT`)
   - `assignee_id UUID REFERENCES users(id)`
   - `proposed_by_agent BOOLEAN NOT NULL DEFAULT FALSE`
   - `agent_proposal_reason TEXT`
   - `human_override_reason TEXT`
   - `lock_version INT NOT NULL DEFAULT 1`
   - `created_by UUID REFERENCES users(id)`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
   - `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

2. **`task_events`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE`
   - `event_type VARCHAR(50) NOT NULL` (`STATUS_CHANGE`, `ASSIGNED`, `EVIDENCE_LINKED`, `HUMAN_OVERRIDE`, `PROPOSAL_ACCEPTED`)
   - `old_status VARCHAR(50)`
   - `new_status VARCHAR(50)`
   - `actor_type VARCHAR(50) NOT NULL` (`HUMAN`, `AGENT`, `SYSTEM`)
   - `actor_id VARCHAR(255) NOT NULL`
   - `reason TEXT`
   - `metadata JSONB`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

3. **`agent_sessions`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `client_platform VARCHAR(50) NOT NULL` (`ANTIGRAVITY`, `CLAUDE`, `CHATGPT`, `LOCAL_AGENT`, `OTHER`)
   - `task_id UUID REFERENCES tasks(id) ON DELETE SET NULL`
   - `started_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
   - `ended_at TIMESTAMPTZ`
   - `status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'` (`ACTIVE`, `COMPLETED`, `ABORTED`)
   - `summary TEXT`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

4. **`agent_interactions`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `session_id UUID NOT NULL REFERENCES agent_sessions(id) ON DELETE CASCADE`
   - `task_id UUID REFERENCES tasks(id) ON DELETE SET NULL`
   - `interaction_type VARCHAR(50) NOT NULL` (`TOOL_CALL`, `ARTIFACT_SAVE`, `DECISION_PROPOSAL`, `EVIDENCE_RECORD`)
   - `tool_name VARCHAR(100)`
   - `artifact_id UUID REFERENCES artifacts(id)`
   - `details JSONB`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

5. **`implementation_records`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE`
   - `session_id UUID REFERENCES agent_sessions(id)`
   - `agent_platform VARCHAR(50) NOT NULL`
   - `summary TEXT NOT NULL`
   - `modified_files JSONB NOT NULL DEFAULT '[]'::jsonb`
   - `unresolved_issues JSONB NOT NULL DEFAULT '[]'::jsonb`
   - `candidate_status VARCHAR(50) NOT NULL DEFAULT 'DONE_UNVERIFIED'`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

6. **`test_executions`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE`
   - `session_id UUID REFERENCES agent_sessions(id)`
   - `test_type VARCHAR(50) NOT NULL` (`UNIT`, `INTEGRATION`, `E2E`, `CONTRACT`)
   - `framework VARCHAR(50)` (`JUNIT5`, `VITEST`, `GO_TEST`, `POSTMAN`)
   - `passed BOOLEAN NOT NULL`
   - `total_tests INT NOT NULL`
   - `passed_tests INT NOT NULL`
   - `failed_tests INT NOT NULL`
   - `duration_ms BIGINT NOT NULL`
   - `error_summary TEXT`
   - `log_artifact_id UUID REFERENCES artifacts(id)`
   - `recorded_by VARCHAR(255) NOT NULL`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

7. **`debugging_records`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `task_id UUID REFERENCES tasks(id) ON DELETE SET NULL`
   - `failure_type VARCHAR(50) NOT NULL` (`DEPLOYMENT`, `TEST`, `BUILD`)
   - `classification VARCHAR(50) NOT NULL` (`BUILD_FAILURE`, `RUNTIME_CRASH`, `CONFIG_ERROR`, `TEST_FAILURE`)
   - `failed_deployment_id UUID REFERENCES deployments(id)`
   - `resolved_deployment_id UUID REFERENCES deployments(id)`
   - `root_cause_analysis TEXT`
   - `attempt_summary TEXT`
   - `status VARCHAR(50) NOT NULL DEFAULT 'INVESTIGATING'` (`INVESTIGATING`, `PATCH_APPLIED`, `RESOLVED`, `ABANDONED`)
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
   - `resolved_at TIMESTAMPTZ`

8. **`task_links`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE`
   - `link_type VARCHAR(50) NOT NULL` (`DECISION`, `ARTIFACT`, `COMMIT`, `DEPLOYMENT`)
   - `target_id VARCHAR(255) NOT NULL`
   - `target_label VARCHAR(255)`
   - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

9. **`action_requests`**:
   - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
   - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
   - `agent_token_id UUID REFERENCES mcp_tokens(id)`
   - `action_type VARCHAR(100) NOT NULL` (`TRIGGER_DEPLOYMENT`, `CREATE_PR`, `RUN_TEST_SUITE`, `RESTART_SERVICE`)
   - `parameters JSONB NOT NULL DEFAULT '{}'::jsonb`
   - `is_destructive BOOLEAN NOT NULL DEFAULT FALSE`
   - `status VARCHAR(50) NOT NULL DEFAULT 'PENDING'` (`PENDING`, `APPROVED`, `REJECTED`, `EXECUTED`, `FAILED`)
   - `requested_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`
   - `reviewed_by UUID REFERENCES users(id)`
   - `reviewed_at TIMESTAMPTZ`
   - `review_notes TEXT`
   - `execution_result JSONB`

10. **`code_symbols` & `dependency_edges`**:
    - `code_symbols`: file_path, language, symbol_name, symbol_type (`CLASS`, `INTERFACE`, `FUNCTION`, `METHOD`), signature, start_line, end_line.
    - `dependency_edges`: source_path, target_path, dependency_type (`IMPORT`, `INHERITANCE`, `API_CALL`, `TEST_OF`), confidence (`HIGH`, `MEDIUM`, `HEURISTIC`), metadata.

11. **`agent_handoffs`**:
    - `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`
    - `project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE`
    - `task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE`
    - `from_actor_type VARCHAR(50) NOT NULL` (`HUMAN`, `AGENT`)
    - `from_actor_id VARCHAR(255) NOT NULL`
    - `to_actor_type VARCHAR(50) NOT NULL` (`HUMAN`, `AGENT`)
    - `to_actor_id VARCHAR(255) NOT NULL`
    - `context_package_id UUID`
    - `notes TEXT`
    - `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`

---

## 3. Core Workflow Mechanisms

### 3.1 Task State Machine & Evidence Rule
The state machine strictly validates state transitions:
- `BACKLOG` -> `READY`
- `READY` -> `IN_PROGRESS`
- `IN_PROGRESS` -> `IN_REVIEW`, `BLOCKED`, `DONE_UNVERIFIED`
- `BLOCKED` -> `READY`, `IN_PROGRESS`
- `IN_REVIEW` -> `IN_PROGRESS`, `DONE_UNVERIFIED`, `READY`
- `DONE_UNVERIFIED` -> `VERIFIED`:
  - **Forbidden for agents alone**: If an agent requests `VERIFIED`, it is coerced to `DONE_UNVERIFIED` with a note.
  - **Required Evidence**:
    1. At least one passing `test_executions` record linked to the task.
    2. If code changed, a linked commit or implementation record.
    3. If deployment relevant, a successful deployment.
    4. OR explicit human override with a non-blank `human_override_reason`.
- Optimistic locking: Updates check `WHERE id = :id AND lock_version = :expectedLockVersion`, bumping `lock_version = lock_version + 1`.

### 3.2 Task-Aware Context Orchestration
The Context Orchestrator is augmented with 5 modes:
- `IMPLEMENTATION`: Focuses on approved decisions, file symbols, and OpenAPI contracts.
- `REVIEW`: Focuses on diffs, ADR compliance, and verification evidence.
- `DEBUGGING`: Assembles error summaries, deployment-specific logs, prior attempts, and DB decisions.
- `TESTING`: Focuses on API contracts, test executions, and affected test suites.
- `DEPLOYMENT`: Focuses on environment config names, dependencies, and release notes.

### 3.3 Debugging Context Packages
When a failure occurs, the orchestrator retrieves logs strictly matching the failed deployment ID (with untrusted boundary markers). Logs from other deployments are rejected by contract. Prior debugging attempts for similar classifications are included with their outcomes to prevent redundant cycles.

### 3.4 Code Intelligence
- JavaParser extracts classes, methods, imports, and calls from `.java` files.
- Manifest parser extracts dependencies from `pom.xml` and `package.json`.
- OpenAPI parser extracts path operations and data schemas.
- `analyze_change_impact`: given a list of changed files, queries dependency edges and returns affected modules, APIs, test files, and tasks with explicit confidence ratings (`HIGH`, `MEDIUM`, `HEURISTIC`).

### 3.5 Action Request Framework (`EXECUTE_APPROVED_ACTIONS`)
- Agents submit requests with parameters and `is_destructive` flag.
- Requests enter the Approval Inbox in `PENDING` state.
- Human approves or rejects. Destructive actions require a confirmation step.
- Execution happens only after human approval.

### 3.6 MCP Tool Suite Expansion (8 New Tools -> 25 Tools Total)
1. `update_task_status`: Updates task state enforcing evidence rules and optimistic locking.
2. `record_implementation_result`: Summarizes files modified and issues; transitions task to `DONE_UNVERIFIED`.
3. `record_test_result`: Ingests test run metrics and raw output artifact.
4. `get_task_context`: Returns task overview, decisions, artifacts, implementation state, and open issues.
5. `get_api_contract`: Returns OpenAPI contracts or interface schemas.
6. `get_project_dependencies`: Returns manifest dependencies and graph.
7. `analyze_change_impact`: Predicts blast radius of code changes.
8. `get_relevant_tests`: Identifies test suites covering changed files or symbols.

---

## 4. Frontend Components & Views
1. **Tasks Dashboard (`TasksPage.tsx`)**:
   - List & Board (Kanban) views with Linear-like density.
   - Hotkeys: `C` (create), `J`/`K` (navigate), `Enter` (open detail).
   - Side drawer detail panel (180ms smooth transition):
     - Separate tabs for **Claims** (agent summaries) vs **Evidence** (test results, commit hashes).
     - Handoff trail visualizer.
     - Debugging records.
2. **Approval Inbox (`ApprovalInboxPage.tsx`)**:
   - Pending action requests, agent-proposed tasks, and agent-proposed decisions.
   - Parameter breakdown and one-click Approve/Reject.
   - Destructive action confirmation modal.
3. **Enhanced Overview & Activity**:
   - Active tasks, stage tracker, integration health badges, and pending approvals counter.
4. **Debugging Package Inspector**:
   - Failure classification, deploy logs, and linked attempts.

---

## 5. Verification Plan & Test Strategy
1. **State Machine & Evidence Enforcement Test**: Verifies `DONE_UNVERIFIED` vs `VERIFIED` evidence rules.
2. **Concurrent Task Update Test**: Tests optimistic locking failures on simultaneous edits.
3. **Action Approval Security Test**: Proves agents cannot execute actions directly.
4. **Debugging Package Assembly Test**: Verifies strict deployment-log scoping and error extraction.
5. **Impact Analysis Test**: Evaluates blast radius prediction on fixture Java/TS code.
6. **Row-Level Security Test**: Ensures cross-project task and session isolation.
7. **End-to-End Scenario Test**: Full lifecycle across architecture, review, implementation, failed deploy, debugging package, fix, test verification, and resolution.

