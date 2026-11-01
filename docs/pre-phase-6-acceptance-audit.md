# Pre-Phase 6 Acceptance Audit & Phase 4 Gap Analysis

**Platform**: Aether (Unified Engineering Context Platform)  
**Audit Date**: October 31, 2026  
**Auditor**: Advanced Agentic Coding Engine (Antigravity)  
**Target Milestone**: Phase 6 Readiness Gate  

---

## 1. Executive Verdict

### **VERDICT: NOT READY FOR PHASE 6**

While the core domain architecture, PostgreSQL Row-Level Security, multi-agent state machines, evidence-based verification enforcement, and local ONNX embedding models are fully implemented and passing all 54 backend and 7 frontend tests, this audit has identified **4 critical functional and security gaps** that must be resolved before proceeding to Phase 6 (Production Hardening & Deployment):

1. **Cross-Service False Resolution in Debugging**: `DebuggingService.onDeploymentSuccess` marks debugging records resolved across the entire project without matching `serviceId` or `environment`. A successful deployment of Service B will falsely resolve open crash records for Service A.
2. **Race Condition in Action Request Execution**: `ActionRequestRepository.updateReview` lacks atomic conditional updates (`WHERE status = 'PENDING'`) and optimistic locking. Concurrent approval requests could execute destructive actions twice.
3. **Missing Centralized Action Audit Logging**: Action request submissions, approvals, rejections, and executions write exclusively to `action_requests`; they do not emit events to `timeline_events` or `audit_events`.
4. **Render Integration Live Polling Stubbed**: `RenderAdapter.retrieve` returns `List.of()` instead of executing HTTP polling requests against `api.render.com`.

---

## 2. Phase 5 Acceptance Matrix

Verification of all 14 Phase 5 constitutional and workflow coordination requirements:

| # | Requirement | Implementation Location | Test Method & Class | Test Output | Status | Limitations & Missing Cases |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | **Agent cannot directly set VERIFIED** | `TaskStateMachine.java` (`evaluateTargetStatus`, L63)<br>`TaskService.java` (`updateTaskStatus`, L141) | `TaskStateMachineAndEvidenceTest.java`<br>`testAgentCannotDirectlySetVerifiedWithoutEvidence` | Status coerced from `VERIFIED` to `DONE_UNVERIFIED`. Event logged. | **PASS** | None. Enforced for both REST and MCP callers. |
| **2** | **Human override requires authorization & nonblank reason** | `TaskStateMachine.java` (`evaluateTargetStatus`, L74)<br>`TaskService.java` (`updateTaskStatus`, L146) | `TaskStateMachineAndEvidenceTest.java`<br>`testHumanRequiresEvidenceOrOverrideForVerified` | Blank reason throws `IllegalStateException`. Valid reason transitions to `VERIFIED`. | **PASS** | `TaskController` accepts `actorType` in request DTO rather than resolving strictly from security context. |
| **3** | **Human override visibly distinguished in UI** | `TasksPage.tsx` (L700–715 alert banner; L740–840 tabs) | `frontend/src/test/components.test.tsx`<br>`renders distinct badges for DONE_UNVERIFIED and VERIFIED states` | Amber "Unverified Claim" vs Green "Verified Evidence" vs Warning "Human Override Recorded". | **PASS** | Visual representation complete. |
| **4** | **Passing test evidence linked to task & commit** | `AgentTrackingService.java` (`recordTestExecution`, L170)<br>`TaskService.java` (`linkTarget`, L186) | `Phase5WorkflowCoordinationEndToEndTest.java`<br>`testFullEndToEndWorkflowCoordinationScenario` | Test logs saved as immutable artifact; task auto-transitions to `VERIFIED`; commit SHA linked. | **PASS** | None. |
| **5** | **Optimistic locking prevents lost updates** | `TaskRepository.java` (`update`, L66)<br>`TaskService.java` (`updateTaskStatus`, L135) | `ConcurrentTaskUpdateTest.java`<br>`testOptimisticLockingPreventsLostUpdates` | Second concurrent update with stale `lock_version` throws `IllegalStateException`. | **PASS** | None. Enforced on `lock_version`. |
| **6** | **Debugging packages strictly deployment-scoped** | `DebuggingService.java` (`assembleDebuggingPackage`, L60–120) | `DebuggingPackageAssemblyTest.java`<br>`testDebuggingPackageIsStrictlyDeploymentScoped` | Deploy A package contains Deploy A logs only; Deploy B crash logs strictly excluded. | **PASS** | Boundary markers wrap untrusted log lines; secrets masked. |
| **7** | **Successful deploys resolve only associated debugging records** | `DebuggingService.java` (`onDeploymentSuccess`, L185–203) | `Phase5WorkflowCoordinationEndToEndTest.java` (L155–163) | Tested in single-service scenario. | **FAIL** | **Critical Gap**: `onDeploymentSuccess` scans all project failures without filtering by `serviceId` or `environment`. |
| **8** | **Agents cannot approve own action requests** | `ActionRequestController.java` (`reviewAction`, L42)<br>`McpToolRegistry.java` (no review tool exposed) | `ActionApprovalSecurityTest.java`<br>`testActionRemainsPendingUntilHumanApproval` | Agent token submits action -> enters `PENDING`. MCP has no review tool. | **PASS** | No unit test attempting direct forged REST POST to `/api/projects/{id}/actions/{id}/review` with agent token. |
| **9** | **Approval bound to exact action parameters** | `ActionRequestService.java` (`reviewAction`, L76)<br>`ReviewActionRequest.java` | `ActionApprovalSecurityTest.java`<br>`testActionRemainsPendingUntilHumanApproval` | Stored `existing.parameters()` dispatched to adapter. Reviewer cannot alter parameters. | **PASS** | None. Parameters are immutable on review. |
| **10** | **Parameter modifications invalidate prior approvals** | `ActionRequestRepository.java` (L82–106) | Architectural Inspection | `action_requests` has no parameter update query or endpoint; new parameters require new request. | **PASS** | In-place parameter tampering is impossible via API. |
| **11** | **Destructive actions cannot execute twice on retry** | `ActionRequestService.java` (`reviewAction`, L66) | `ActionApprovalSecurityTest.java`<br>`testActionRemainsPendingUntilHumanApproval` | Sequential retry on non-PENDING request throws `IllegalStateException`. | **FAIL (Concurrent)** | `ActionRequestRepository.updateReview` lacks `WHERE status = 'PENDING'` atomic clause and optimistic locking. Race condition possible. |
| **12** | **All action requests, approvals, and rejections audited** | `ActionRequestRepository.java` (`updateReview`, L91)<br>`action_requests` table | `ActionApprovalSecurityTest.java` | `requested_at`, `reviewed_by`, `reviewed_at`, `review_notes`, and `execution_result` persisted. | **PARTIAL** | Writes to `action_requests` only; fails to call `AuditService` or `TimelineService`. |
| **13** | **MCP tools enforce authorization server-side** | `McpSecurityService.java` (`enforcePermission`)<br>`McpToolRegistry.java` (L519–1484) | `McpProtocolAndSecurityTest.java`<br>`testPermissionsEnforcement`<br>`McpDeploymentToolsTest.java` | Lacking permission throws `ProjectAccessDeniedException`. | **PASS** | Server-side enforcement verified across all 28 tools. |
| **14** | **Task history consistent across agents & platforms** | `TaskService.java` (`getTaskContext`, L214)<br>`task_events`, `agent_sessions`, `agent_handoffs` | `Phase5WorkflowCoordinationEndToEndTest.java`<br>`testFullEndToEndWorkflowCoordinationScenario` | Context response returns unified history spanning Claude, Antigravity, and Human actions. | **PASS** | None. |

---

## 3. Phase 4 Outstanding Verification Matrix

| Requirement | Implementation Component | Verification Test | Tested Against Real Provider? | Evidence & Verdict |
| :--- | :--- | :--- | :--- | :--- |
| **GitHub Webhook Delivery & Signature** | `GitHubAdapter.java`<br>`IntegrationReliabilityService.java` | `WebhookSignatureAndSecurityTest.java`<br>`testHmacSha256Verification`<br>`testGitHubAdapterSecurityAndReplay` | **NO (Simulated)** | Genuine HMAC-SHA256 crypto, replay detection via `processed_events`, and header parsing verified against mock payloads. Live GitHub webhook delivery over public internet is **UNVERIFIED**. |
| **Render Deployment & Failure Logs** | `RenderAdapter.java`<br>`DeploymentRepository.java` | `RenderDeploymentAndLogsTest.java`<br>`testRenderFailureClassification` | **NO (Mocked)** | Log ingestion, failure regex classification, and artifact persistence verified. However, `RenderAdapter.retrieve` returns `List.of()` and live HTTP calls to `api.render.com` are **NOT IMPLEMENTED**. |
| **Local-Only Zero Outbound Calls** | `LocalStorageService.java`<br>`LocalOnnxSemanticEmbeddingProvider.java`<br>`agent/` (Go) | `LocalAgentIntegrationTest.java`<br>`testAgentLifecycle`<br>Codebase Inspection | **YES (Verified)** | All embeddings run locally via ONNX Runtime (`model.onnx`); artifacts store on local disk; PostgreSQL is local. Zero outbound internet telemetry or external API calls occur in `LOCAL_FIRST` mode. |
| **Secret Exclusions & .aetherignore** | `agent/filter/filter.go`<br>`SecretDetectionService.java` | `agent/filter/filter_test.go`<br>`SecretDetectionAndRedactionTest.java` | **YES (Verified)** | Case-insensitive exclusions (`.env`, certificates, SSH keys) verified. `.aetherignore` cannot override default ignore rules. **Limitation**: `agent/scanner/scanner.go` does not check for symlinks escaping root directory. |
| **Credential Encryption & Redaction** | `CredentialEncryptionService.java`<br>`integration_credentials` table | `CredentialEncryptionTest.java`<br>`testEncryptDecryptRoundtrip`<br>`testTamperedCiphertextFails`<br>`testMaskedPreviews` | **YES (Verified)** | AES-256-GCM authenticated encryption with 12-byte IV and 16-byte tag. Tampering throws `SecurityException`. Plaintext masked in API previews (`ghp_...cdef`). |
| **Webhook Replay Protection & Idempotency** | `IntegrationReliabilityService.java`<br>`processed_events` table | `WebhookSignatureAndSecurityTest.java`<br>`testGitHubAdapterSecurityAndReplay` | **YES (Verified)** | Deduplication keyed on `(project_id, provider, event_id, idempotency_key)`. Replay attempts return null without re-processing. |
| **DLQ Recovery & Reconciliation** | `IntegrationReliabilityService.java`<br>`dead_letter_events` table | `IntegrationReliabilityTest.java`<br>`testDeadLetterQueueAndRetry` | **YES (Verified)** | Failed event routing to DLQ with error summary, incremented retry counter, and successful replay transitions status to `RETRIED`. |

---

## 4. Permission Reconciliation

### 4.1 Discrepancy Analysis

A discrepancy was identified between early Phase 2 documentation and the constitutional definition:

- **Constitution (`docs/CONSTITUTION.md` Section "PERMISSIONS")**:
  1. `READ_PROJECT_CONTEXT`
  2. `READ_ARTIFACTS`
  3. `SAVE_ARTIFACTS`
  4. `RECORD_DECISIONS`
  5. `READ_DEPLOYMENT_DATA`
  6. `EXECUTE_APPROVED_ACTIONS`

- **Phase 2 Report (`docs/phase-2-report.md` Section 1.3)**:
  Listed: `READ_PROJECT_CONTEXT`, `READ_ARTIFACTS`, `READ_DECISIONS`, `SAVE_ARTIFACTS`, `RECORD_DECISIONS`, `ADMIN_INTEGRATION`.

### 4.2 Authoritative Permission Model

The Constitution's **6 permissions** are the definitive model:

1. **`READ_PROJECT_CONTEXT`**: Read project overview, tech stack, structured facts, timeline, ADR list, task context, code dependencies, and impact analysis.
2. **`READ_ARTIFACTS`**: Retrieve raw artifact content, chunk expansions, OpenAPI contracts, and version diffs.
3. **`SAVE_ARTIFACTS`**: Append immutable artifact versions, conversation transcripts, review findings, report implementation deliverables, and record test runs.
4. **`RECORD_DECISIONS`**: Propose Architectural Decision Records (strictly `PROPOSED` state).
5. **`READ_DEPLOYMENT_DATA`**: Read build and runtime deployment logs with failure highlights.
6. **`EXECUTE_APPROVED_ACTIONS`**: Submit high-privilege action requests for human approval in the Approval Inbox.

### 4.3 Why `READ_DECISIONS` and `ADMIN_INTEGRATION` Were Retired
- **`READ_DECISIONS`**: Subsumed by `READ_PROJECT_CONTEXT`. Reading decisions is an inherent part of reading project memory and architecture.
- **`ADMIN_INTEGRATION`**: Removed from MCP scope. Connecting, pausing, and disconnecting provider credentials is an administrative operation restricted exclusively to authenticated human users over REST, never callable by autonomous AI agents.

### 4.4 Verification of All 28 MCP Tools

Every tool in `McpToolRegistry.java` aligns with the authoritative 6-permission model:

| Tool Name | Required Permission | Validated In Code? |
| :--- | :--- | :--- |
| `list_projects` | `READ_PROJECT_CONTEXT` | Yes (L519) |
| `get_project_overview` | `READ_PROJECT_CONTEXT` | Yes (L533) |
| `get_project_context` | `READ_PROJECT_CONTEXT` | Yes (L557) |
| `get_project_status` | `READ_PROJECT_CONTEXT` | Yes (L583) |
| `search_project_memory` | `READ_PROJECT_CONTEXT` | Yes (L607) |
| `get_context` | `READ_PROJECT_CONTEXT` | Yes (L634) |
| `expand_chunk` | `READ_ARTIFACTS` | Yes (L664) |
| `get_original_artifact` | `READ_ARTIFACTS` | Yes (L720) |
| `get_architecture` | `READ_PROJECT_CONTEXT` | Yes (L781) |
| `get_decisions` | `READ_PROJECT_CONTEXT` | Yes (L822) |
| `get_recent_activity` | `READ_PROJECT_CONTEXT` | Yes (L855) |
| `save_project_artifact` | `SAVE_ARTIFACTS` | Yes (L875) |
| `save_conversation_output` | `SAVE_ARTIFACTS` | Yes (L928) |
| `record_decision` | `RECORD_DECISIONS` | Yes (L971) |
| `record_review_finding` | `SAVE_ARTIFACTS` | Yes (L1003) |
| `get_latest_deployment` | `READ_PROJECT_CONTEXT` | Yes (L1072) |
| `get_deployment_status` | `READ_PROJECT_CONTEXT` | Yes (L1097) |
| `get_deployment_logs` | `READ_DEPLOYMENT_DATA` | Yes (L1132) |
| `get_previous_failures` | `READ_PROJECT_CONTEXT` | Yes (L1208) |
| `get_relevant_files` | `READ_PROJECT_CONTEXT` | Yes (L1233) |
| `update_task_status` | `SAVE_ARTIFACTS` | Yes (L1288) |
| `record_implementation_result` | `SAVE_ARTIFACTS` | Yes (L1315) |
| `record_test_result` | `SAVE_ARTIFACTS` | Yes (L1354) |
| `get_task_context` | `READ_PROJECT_CONTEXT` | Yes (L1385) |
| `get_api_contract` | `READ_ARTIFACTS` | Yes (L1414) |
| `get_project_dependencies` | `READ_PROJECT_CONTEXT` | Yes (L1438) |
| `analyze_change_impact` | `READ_PROJECT_CONTEXT` | Yes (L1461) |
| `get_relevant_tests` | `READ_PROJECT_CONTEXT` | Yes (L1484) |

---

## 5. Security & Architectural Findings

### Finding SEC-01 (High): Action Execution Concurrency Race Condition
- **Location**: `ActionRequestService.java` (L61–79) & `ActionRequestRepository.java` (L82–106).
- **Issue**: `reviewAction` checks `existing.status() == ActionRequestStatus.PENDING` in application memory. However, the database update in `ActionRequestRepository.updateReview` executes:
  ```sql
  UPDATE action_requests SET status = :status, ... WHERE project_id = :projectId AND id = :id
  ```
  It lacks an atomic `AND status = 'PENDING'` predicate and `action_requests` has no `lock_version` column.
- **Risk**: Two rapid concurrent review requests (e.g. double click or automated API call) can both pass the in-memory check and trigger `executeApprovedAction()` twice. For destructive actions (e.g. drop database, deploy rollback), duplicate execution is hazardous.
- **Remediation**: Add `AND status = 'PENDING'` to `updateReview` and check rows affected (must equal 1), or add optimistic locking.

### Finding SEC-02 (Medium): Local Agent Scanner Symlink Path Traversal
- **Location**: `agent/scanner/scanner.go` (L68–100).
- **Issue**: `filepath.Walk` is used without calling `filepath.EvalSymlinks`.
- **Risk**: A symbolic link inside the repository pointing to a location outside the project root (e.g. `symlink -> /etc/shadow` or `symlink -> ~/.ssh`) could be opened during content hashing if the link name doesn't match an exclusion pattern.
- **Remediation**: Before hashing or reading file contents, check `info.Mode() & os.ModeSymlink != 0` and verify the resolved target path resides within `s.rootDir`.

### Finding AUDIT-01 (Medium): Action Requests Not Emitted to Central Timeline
- **Location**: `ActionRequestService.java` (L32–80).
- **Issue**: Action requests, approvals, and rejections are persisted only to `action_requests`. `TimelineService` and `AuditService` are not called.
- **Impact**: Action executions do not appear in the immutable `audit_events` or `timeline_events` streams visible on `/projects/{id}/timeline`.
- **Remediation**: Inject `TimelineService` and `AuditService` into `ActionRequestService` and log all lifecycle transitions.

### Finding GAP-01 (High): Cross-Service Resolution in DebuggingService
- **Location**: `DebuggingService.java` (L185–203).
- **Issue**: `onDeploymentSuccess` scans all failed deployments across the project:
  ```java
  List<DeploymentRecord> recentDeploys = deploymentRepository.listByProject(projectId, 10);
  ```
  It resolves all non-resolved debugging records regardless of which service or environment they belong to.
- **Impact**: If a project contains `api-gateway` and `billing-service`, a successful deployment of `billing-service` will mark open crash records for `api-gateway` as resolved.
- **Remediation**: Query `deploymentRepository.findById(projectId, successfulDeployId)` to get `successfulDeploy.serviceId()` and `successfulDeploy.environment()`, and only resolve debugging records associated with matching service and environment.

---

## 6. Real-Provider Multi-Agent Acceptance Evaluation

Evaluation of the 12-step target workflow from Section 5:

| Step | Description | Implementation Status | Evidence Level | Verification Detail |
| :--- | :--- | :--- | :--- | :--- |
| **1** | ChatGPT saves architecture artifact | Implemented | **Simulated MCP** | Tested via JSON-RPC `save_project_artifact` with ChatGPT token in `McpHandoffFlowTest`. Not run against live chatgpt.com. |
| **2** | Claude retrieves and reviews it | Implemented | **Simulated MCP** | Tested via JSON-RPC `get_original_artifact` and `record_review_finding` with Claude token in `McpHandoffFlowTest`. |
| **3** | Human approves architecture / ADR | Implemented | **Real DB Integration** | Tested via `decisionService.submitForReview` + `decisionService.approveDecision` in PostgreSQL. |
| **4** | Antigravity retrieves approved architecture | Implemented | **Simulated MCP** | Tested via JSON-RPC `get_architecture` returning approved version. |
| **5** | Implementation is recorded | Implemented | **Real DB Integration** | Tested via `agentTrackingService.recordImplementation`, transitioning task to `DONE_UNVERIFIED`. |
| **6** | GitHub commit is captured | Implemented | **Simulated Link** | Tested via `taskService.linkTarget(..., COMMIT, "git-sha-7a8b9c")`. Not received via live internet webhook. |
| **7** | Render deployment fails | Implemented | **Real Domain Ingestion** | Tested via `renderAdapter.ingestDeployment` classifying failure and saving raw logs. |
| **8** | Debugging package retrieves correct logs | Implemented | **Real DB Integration** | Tested via `debuggingService.assembleDebuggingPackage`, verifying deployment log isolation. |
| **9** | Agent records the fix | Implemented | **Real DB Integration** | Tested via `recordImplementation` with session tracking. |
| **10** | Passing tests are recorded | Implemented | **Real DB Integration** | Tested via `agentTrackingService.recordTestExecution(passed=true)`, auto-verifying task. |
| **11** | Successful deployment is captured | Implemented | **Real Domain Ingestion** | Tested via `deploymentRepository.save(status="LIVE")` and `debuggingService.onDeploymentSuccess`. |
| **12** | Task retains complete history | Implemented | **Real DB Integration** | Tested via `taskService.getTaskContext`, returning decisions, artifacts, prior attempts, and handoffs. |

### Live Provider Procedure (When Credentials Available)

To execute this against real third-party providers:
1. **GitHub**: Create a GitHub App or Webhook targeting `https://<public-url>/api/webhooks/github` with secret `gh_secret_123`. Perform `git push` on linked repository. Verify signature and event ingestion.
2. **Render**: Connect Render service with API key `rnd_...` and service ID `srv-...`. Set polling interval. Trigger deployment via Render dashboard. Verify logs and deployment records appear in Aether.
3. **Claude Desktop**: Add `mcpServers.aether` to `claude_desktop_config.json` using `npx -y mcp-remote http://localhost:8080/api/mcp --header Authorization:Bearer <token>`. Ask Claude to call `get_project_context`.
4. **Antigravity**: Add `.agents/mcp_config.json` with Bearer token. Prompt agent to run `update_task_status`.

---

## 7. Prioritized Remediation List

Before Phase 6 begins, complete these remediation items in priority order:

### Priority 1: Concurrency & Scope Fixes (Mandatory before Phase 6)
1. **Fix `DebuggingService.onDeploymentSuccess`**:
   Fetch `successfulDeploy` and match `serviceId` and `environment` before resolving debugging records.
2. **Make `ActionRequestRepository.updateReview` Atomic**:
   Change query to:
   ```sql
   UPDATE action_requests
   SET status = :status, reviewed_by = :reviewedBy, reviewed_at = NOW(), review_notes = :reviewNotes, execution_result = :executionResult::jsonb
   WHERE project_id = :projectId AND id = :id AND status = 'PENDING'
   ```
   Throw `IllegalStateException` if rows affected is 0.
3. **Emit Central Audit & Timeline Events in `ActionRequestService`**:
   Call `timelineService.append(...)` and `auditService.log(...)` on action submission, approval, and rejection.

### Priority 2: Security & Provider Verification
4. **Symlink Validation in Go Scanner**:
   In `agent/scanner/scanner.go`, add `filepath.EvalSymlinks` and confirm the target directory begins with `s.rootDir`.
5. **Implement Real Render REST WebClient**:
   Replace `return List.of()` in `RenderAdapter.retrieve` with Spring `WebClient` calling `GET https://api.render.com/v1/services/{serviceId}/deploys`.

---

## 8. Exact Reproduction Commands

All test suites and verification checks can be reproduced locally with these exact commands:

```powershell
# 1. Full Backend Test Suite (54 tests, PostgreSQL Testcontainers / pgvector)
cd backend
mvn test

# 2. Phase 5 Workflow Coordination & Evidence Tests Specifically
mvn test -Dtest="com.context_platform.aether.workflow.*Test"

# 3. Phase 4 Integrations & Reliability Tests
mvn test -Dtest="com.context_platform.aether.integration.*Test"

# 4. Local Go Agent Tests (Scanner, Filter, Config, Runner)
cd ../agent
go test ./...

# 5. Frontend Unit Tests (Vitest)
cd ../frontend
npm test -- --run

# 6. Frontend Production Build (TypeScript Compilation & Vite bundle)
npm run build
```

---

## 9. Final Recommendation

**NOT READY FOR PHASE 6.**

Do not proceed to Phase 6 until Priority 1 items (Service-scoped debugging resolution, atomic action approval concurrency, and action timeline audit logging) are implemented and verified. Once closed, Aether will possess an airtight foundation for production deployment.

