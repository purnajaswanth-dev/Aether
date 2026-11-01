# Integration and Local Agent Testing Guide

This guide provides reproducible manual testing procedures and automated verification commands for all Phase 4 external integrations and the direct local project agent (`aether-agent`).

---

## 1. Local Project Agent (`aether-agent`)

The local project agent is written in Go 1.21+ and compiled to a static binary located in `agent/`.

### 1.1 Run Agent Unit and Integration Tests

Run the full suite of Go tests covering configuration management, path filtering, file scanner & incremental hashing, and command execution:

```bash
cd agent
go test -v ./...
```

Expected output:
```
=== RUN   TestAgentConfig_SaveAndLoad
--- PASS: TestAgentConfig_SaveAndLoad (0.01s)
=== RUN   TestPathFilter_SecretPatterns
--- PASS: TestPathFilter_SecretPatterns (0.00s)
=== RUN   TestPathFilter_BuildArtifacts
--- PASS: TestPathFilter_BuildArtifacts (0.00s)
=== RUN   TestScanner_ScanProject
--- PASS: TestScanner_ScanProject (0.01s)
=== RUN   TestCommandRunner_ExecuteSuccess
--- PASS: TestCommandRunner_ExecuteSuccess (0.02s)
=== RUN   TestCommandRunner_ExecuteFailure
--- PASS: TestCommandRunner_ExecuteFailure (0.02s)
PASS
ok  	aether-agent/config	0.18s
ok  	aether-agent/filter	0.15s
ok  	aether-agent/scanner	0.19s
ok  	aether-agent/runner	0.22s
```

### 1.2 Build the Agent Binary

```bash
cd agent
go build -o aether-agent.exe main.go
```

### 1.3 Test Pairing Flow

1. In the Aether UI, navigate to **Integrations & Connected Agents** > **Workstation Agent**.
2. Click **Pair New Workstation**.
3. In your local terminal, run:
   ```bash
   ./agent/aether-agent.exe pair -server http://localhost:8080 -project <PROJECT_ID>
   ```
4. Note the 8-character user code printed to your terminal (e.g., `A1B2-C3D4`).
5. Enter the code in the Aether web dialog and click **Authorize Workstation**.
6. Observe terminal status changing to `[SUCCESS] Workstation paired successfully! Token saved to .aether/agent.json`.

### 1.4 Test Command Execution Tracking

Run any local test command using the agent:

```bash
./agent/aether-agent.exe run -- mvn test -Dtest=CredentialEncryptionTest
```

The agent will capture execution time, stdout, stderr, and exit code, and send the verification record to Aether without uploading environment secrets.

### 1.5 Test Air-Gapped / Local-Only Mode

To ensure zero outbound network calls in restricted environments:

```bash
./agent/aether-agent.exe run --local-only --listen 127.0.0.1:8124
```

Verify that the local inspection server starts on `127.0.0.1:8124` and confirms `Strict air-gapped mode active: Zero external network transmissions`.

---

## 2. GitHub Webhook Ingestion & HMAC-SHA256 Verification

GitHub webhook requests are cryptographically authenticated via `X-Hub-Signature-256`.

### 2.1 Test Valid HMAC-SHA256 Ingestion

Using PowerShell or bash with OpenSSL:

```bash
# 1. Payload
PAYLOAD='{"action":"push","repository":{"full_name":"test-org/aether"},"head_commit":{"id":"a1b2c3d4e5f6","message":"feat: implement RLS policies","author":{"name":"Antigravity"}}}'

# 2. Compute HMAC-SHA256 with secret "my-webhook-secret"
SECRET="my-webhook-secret"
SIGNATURE=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$SECRET" | sed 's/^.* //')

# 3. Post to Aether Webhook endpoint
curl -X POST "http://localhost:8080/api/webhooks/github/<PROJECT_ID>" \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -H "X-GitHub-Delivery: evt-001" \
  -H "X-Hub-Signature-256: sha256=$SIGNATURE" \
  -d "$PAYLOAD"
```

Expected response: `HTTP 200 OK` with JSON `{"status":"ACCEPTED","deliveryId":"evt-001"}`.

### 2.2 Test Replay and Deduplication Protection

Send the exact same request again with the same `X-GitHub-Delivery: evt-001`.
Expected response: `HTTP 200 OK` with `{"status":"DEDUPLICATED"}` or event ignored idempotently.

### 2.3 Test Invalid Signature Rejection

Send a modified payload with an invalid signature:
```bash
curl -X POST "http://localhost:8080/api/webhooks/github/<PROJECT_ID>" \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -H "X-GitHub-Delivery: evt-002" \
  -H "X-Hub-Signature-256: sha256=invalid_hash" \
  -d "$PAYLOAD"
```
Expected response: `HTTP 401 Unauthorized` or routed to Dead-Letter Queue with error `Signature verification failed`.

---

## 3. Render Deployment and Log Synchronization

Render Deploy Hooks are inbound-only. Aether polls the Render REST API to capture deployment state and build/runtime logs.

### 3.1 Configure Render Integration

Navigate to **Integrations** > **Platforms** > **Configure Render**:
- **Service ID**: `srv-c0123456789`
- **Render API Key**: `rnd_live_testkey123`

### 3.2 Trigger Deployment Sync

```bash
curl -X POST "http://localhost:8080/api/projects/<PROJECT_ID>/integrations/RENDER/sync" \
  -H "Authorization: Bearer <AUTH_TOKEN>"
```

### 3.3 Verify Deployment in MCP and UI

Verify through MCP tool `get_latest_deployment`:
```json
{
  "serviceId": "srv-c0123456789"
}
```
Expected output:
```json
{
  "externalDeployId": "dep-live-01",
  "status": "LIVE",
  "environment": "production",
  "commitSha": "9f8e7d6c",
  "logArtifactId": "...",
  "failureClassification": "NONE"
}
```

Verify build logs through `get_deployment_logs`:
```json
{
  "deployId": "dep-live-01",
  "tailLines": 50
}
```

---

## 4. Postman Integration & Non-Negotiable Rule 8

Rule 8 dictates that Postman `/environments` must NEVER be requested or stored.

### 4.1 Sync Collection

```bash
curl -X POST "http://localhost:8080/api/projects/<PROJECT_ID>/integrations/POSTMAN/sync" \
  -H "Authorization: Bearer <AUTH_TOKEN>"
```

Verify that the ingested artifact is an OpenAPI / Postman Collection JSON containing requests and test assertions only, with zero API keys or environment variables.

---

## 5. Dockerfile & Compose Verification

Inspect container definitions from local files:

```bash
curl -X POST "http://localhost:8080/api/projects/<PROJECT_ID>/integrations/DOCKER/sync" \
  -H "Authorization: Bearer <AUTH_TOKEN>"
```

Inspect the generated artifact:
- Base image digest hashes
- Multi-stage build target verification
- Exposed ports and service dependency tree
- Confirms zero daemon socket calls (honestly marked `MANUAL` / file-based)

