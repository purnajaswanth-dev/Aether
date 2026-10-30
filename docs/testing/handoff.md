# Multi-Agent MCP Handoff Test Script

**Protocol Baseline**: Model Context Protocol (MCP) Streamable HTTP & SSE  
**Server Endpoint**: `http://localhost:8080/api/mcp`  
**Purpose**: End-to-end verification of multi-agent context exchange, immutable artifact versioning, peer review findings, human approval enforcement, and approved state retrieval.

---

## Prerequisites
1. Ensure Aether backend is running:
   ```bash
   cd backend
   mvn spring-boot:run
   ```
2. In the Aether UI (`http://localhost:5173`), create a project (e.g. `PRJ-001` "Autonomous Cloud Engine").
3. Navigate to **Integrations > Model Context Protocol (MCP)** and generate three scoped bearer tokens:
   - **Client A Token** (`ANTIGRAVITY`): `aeth_mcp_client_a_...`
   - **Client B Token** (`CLAUDE`): `aeth_mcp_client_b_...`
   - **Client C Token** (`CHATGPT`): `aeth_mcp_client_c_...`

---

## Scripted 4-Step Handoff Execution

### Step 1: Client A (Antigravity) Proposes Architecture
Client A connects via Streamable HTTP and saves the initial architecture document and an Architectural Decision Record (ADR).

**Command**:
```bash
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer <CLIENT_A_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "step-1",
    "method": "tools/call",
    "params": {
      "name": "save_project_artifact",
      "arguments": {
        "title": "Cloud Native Architecture",
        "artifact_type": "ARCHITECTURE",
        "content": "# Cloud Native Architecture Blueprint\n## Topology\n- Modular Spring Boot Monolith\n- PostgreSQL 16 with pgvector\n- Streamable HTTP MCP Endpoint\n## Rules\n- RLS tenant isolation\n- Human-only decision approval\n- Append-only immutability",
        "change_summary": "Initial architecture proposal by Client A"
      }
    }
  }'
```

**Expected Response**:
- Returns `artifact_id`, `version: 1`, and cryptographic `content_hash` (e.g., `a4f9...`).
- Record the returned `artifact_id` and `content_hash`.

Client A proposes the decision:
```bash
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer <CLIENT_A_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "step-1-adr",
    "method": "tools/call",
    "params": {
      "name": "record_decision",
      "arguments": {
        "title": "Adopt Cloud Native Architecture Blueprint",
        "problem_statement": "Need consistent architecture for multi-agent workflows",
        "decision_outcome": "Adopt Blueprint v1",
        "rationale": "Enforces immutable history and provenance",
        "category": "ARCHITECTURE"
      }
    }
  }'
```
**Expected Response**:
- Returns `decision_id`, `public_id` (e.g., `DEC-001`), and `state: "PROPOSED"`.
- *Verification Check*: Notice that the decision is strictly in `PROPOSED` state. AI tokens cannot approve decisions.

---

### Step 2: Client B (Claude) Inspects Architecture and Records Findings
Client B connects, retrieves the identical artifact by ID, verifies the cryptographic SHA-256 hash, and logs peer-review critique without overwriting the architecture.

**Command (Retrieve Artifact)**:
```bash
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer <CLIENT_B_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "step-2-get",
    "method": "tools/call",
    "params": {
      "name": "get_original_artifact",
      "arguments": {
        "artifact_id": "<ARTIFACT_ID>"
      }
    }
  }'
```

**Expected Response**:
- The returned `content_hash` is 100% IDENTICAL to Client A's hash.
- The content is safely enclosed in `<<<UNTRUSTED_DATA_BOUNDARY: ARTIFACT [...]>>>` with the standing notice.

**Command (Record Finding)**:
```bash
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer <CLIENT_B_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "step-2-finding",
    "method": "tools/call",
    "params": {
      "name": "record_review_finding",
      "arguments": {
        "artifact_id": "<ARTIFACT_ID>",
        "artifact_version": 1,
        "finding_title": "HikariCP Connection Pool Exhaustion Risk",
        "finding_details": "Ensure maximum-pool-size is configured for concurrent agent bursts.",
        "severity": "WARNING",
        "suggested_action": "Set maximum-pool-size=25 in application.yml"
      }
    }
  }'
```

**Expected Response**:
- Returns `finding_id`, `reviewed_artifact_id`, and confirmation note: `"Review finding recorded and preserved without modifying original artifact."`
- *Verification Check*: Query the original artifact again. Its version is still 1 and its hash is identical. Immutability is preserved.

---

### Step 3: Human Architect Reviews Findings and Approves
1. Open the Aether Web UI at `http://localhost:5173`.
2. Navigate to **Decisions** > `DEC-001`.
3. Notice the state is `PROPOSED`.
4. Click **Submit for Review** (moves to `UNDER_REVIEW`).
5. Inspect the linked review finding recorded by Client B.
6. Click **Approve Decision** as an authenticated human user.
7. Decision transitions to `APPROVED`.

---

### Step 4: Client C (ChatGPT) Retrieves Approved Architecture
Client C connects to read project context and retrieves the architecture.

**Command**:
```bash
curl -X POST http://localhost:8080/api/mcp \
  -H "Authorization: Bearer <CLIENT_C_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "step-4",
    "method": "tools/call",
    "params": {
      "name": "get_architecture",
      "arguments": {}
    }
  }'
```

**Expected Response**:
- `state`: `"APPROVED"`
- `content`: Enclosed in untrusted data boundary, containing the approved architecture.
- Client C begins implementation adhering to the approved architecture and findings!

---

## Automated Test Verification
This exact 4-step handoff flow is continuously verified in the automated integration test suite:
```bash
cd backend
mvn test -Dtest=McpHandoffFlowTest
```
Result: **100% PASSED**.
