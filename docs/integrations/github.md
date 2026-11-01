# GitHub Integration Specification & Capability Matrix

**Specification Baseline**: GitHub REST API v3 & GitHub Apps Webhooks  
**Auth Model**: GitHub App (Installation Access Token) or Personal Access Token (Fine-grained / Classic)  
**API Baseline URL**: `https://api.github.com`  
**Document Status**: **VERIFIED** against official GitHub documentation as of October 2026.

---

## 1. Executive Summary & Architecture

Aether integrates with GitHub to automatically capture version control activity, pull requests, and CI/CD workflow executions without creating a hard dependency on GitHub (enforcing Constitutional Rule 1: *"GitHub is never required; local projects are first-class"*).

### Choice of Integration Model: GitHub App (Preferred) vs. OAuth App
1. **GitHub App (Recommended)**:
   - **Least-Privilege Scoping**: Grants repository-level access rather than blanket user-account access.
   - **Installation Permissions**: Subscribed only to `metadata` (read), `contents` (read), `pull_requests` (read), and `checks` (read).
   - **Rate Limits**: 5,000 requests/hour per installation (up to 15,000/hour for Enterprise).
   - **Webhooks**: Automatic per-installation webhook multiplexing with cryptographic signature validation.
2. **Personal Access Token (PAT) / OAuth**:
   - Used for individual developer repositories where GitHub App installation is restricted by organization policy.
   - Fine-grained PATs (`github_pat_...`) scoped to specific repositories with read-only permissions.

---

## 2. In-Product Capability Matrix

| Capability | Supported Method | Rate Limit / SLA | In-Product Status | Description |
| :--- | :---: | :---: | :---: | :--- |
| **Push & Commit Capture** | `WEBHOOK` | Instant (< 1s delivery) | **Supported** | Receives commit SHA, author, branch ref, changed file lists, and commit messages. |
| **Pull Request Tracking** | `WEBHOOK` | Instant (< 1s delivery) | **Supported** | Tracks PR opened, closed, merged, title, body, and changed file paths. |
| **CI Workflow Status** | `WEBHOOK` | Instant (< 1s delivery) | **Supported** | Captures `workflow_run` and `check_run` completed statuses, conclusions, and duration. |
| **Repository Inventory** | `POLLING` | 5,000 req/hr | **Supported** | Discovers repository names, default branch, topics, and clone URLs. |
| **Commit History Sync** | `POLLING` | 5,000 req/hr | **Supported** | Reconciles historical commits on initial connection or after network partition. |
| **Code Direct Mutation** | `UNSUPPORTED` | N/A | **Unsupported** | Aether never directly pushes or modifies remote repositories (read-only observer). |

---

## 3. Webhook Security & Reliability Layer

### 3.1 Signature Verification
GitHub webhooks send an HMAC-SHA256 signature in the `X-Hub-Signature-256` HTTP header:
$$\text{Signature} = \text{"sha256="} + \text{HMAC-SHA256}(\text{webhook\_secret}, \text{payload\_bytes})$$
Aether strictly validates this signature using constant-time comparison (`MessageDigest.isEqual`) to prevent timing side-channel attacks. Requests with missing or invalid signatures are rejected with HTTP 401.

### 3.2 Deduplication & Replay Protection
Each GitHub webhook carries a unique delivery GUID in header `X-GitHub-Delivery`:
- Stored in the `processed_events` table with a unique constraint on `(provider, event_id)`.
- Duplicate deliveries (from GitHub retries or replayed packets) are acknowledged with HTTP 200 and ignored idempotently.

### 3.3 Payload Preservation & Untrusted Data Boundary
- Raw webhook payloads are preserved byte-for-byte as immutable artifacts (`ARTIFACT_TYPE: GITHUB_EVENT`) with SHA-256 hash.
- All commit messages, PR descriptions, and author comments are flagged as untrusted data and wrapped in `UNTRUSTED_DATA_BOUNDARY` delimiters before being indexed or served to AI models.

---

## 4. Rate Limits & Outage Reconciliation

- When polling GitHub REST APIs, Aether monitors `x-ratelimit-remaining` and `x-ratelimit-reset`. If remaining calls drop below 50, polling pauses until the reset timestamp.
- If GitHub services experience downtime, Aether records a synchronization checkpoint (`last_event_timestamp` or `last_commit_sha`). Upon reconnection, Aether reconciles missing events via `GET /repos/{owner}/{repo}/events`.

