# Phase 6 Data Protection, Privacy & Integrity Report

**Date:** 2026-11-01  
**Author:** Antigravity / Aether Hardening Team  
**Status:** COMPLETE (Tested & Verified via `DataProtectionTest`)

---

## 1. Executive Summary

Aether enforces strict data integrity, disaster recovery, cryptographic verification, and tenant privacy (including GDPR/CCPA "Right to be Forgotten"). This document defines the backup/restore runbook, artifact tamper verification mechanisms, JSON archive data export architecture, and transactional cascading deletion policies.

---

## 2. Backup & Disaster Recovery Procedures

### 2.1 Full Database Backup (pg_dump)
Run daily or hourly via cron / Kubernetes CronJob:
```bash
# PostgreSQL custom compressed format backup with pgvector support
pg_dump -Fc -v -h "${POSTGRES_HOST:-localhost}" \
        -U "${POSTGRES_USER:-aether}" \
        -d "${POSTGRES_DB:-aether}" \
        -f "/backups/aether_db_$(date +%Y%m%d_%H%M%S).dump"
```

### 2.2 Filesystem Artifact Storage Backup
Sync or archive the local storage directory (containing raw artifact payloads):
```bash
tar -czf "/backups/aether_artifacts_$(date +%Y%m%d_%H%M%S).tar.gz" \
    -C "${AETHER_STORAGE_LOCAL_PATH:-./data/artifacts}" .
```

### 2.3 Disaster Recovery / Point-In-Time Restore Procedure
1. Halt active application containers to prevent concurrent writes:
   ```bash
   docker-compose stop aether-backend
   ```
2. Restore database schema and tables:
   ```bash
   pg_restore -v -c --if-exists -h localhost -U aether -d aether "/backups/aether_db_snapshot.dump"
   ```
3. Extract artifact directory:
   ```bash
   tar -xzf "/backups/aether_artifacts_snapshot.tar.gz" -C ./data/artifacts
   ```
4. Start backend container (Flyway automatically validates schema integrity):
   ```bash
   docker-compose start aether-backend
   ```

---

## 3. Cryptographic Artifact Integrity & Immutability

### 3.1 Checksum Intake & Verification
* Every artifact stored in Aether is hashed with SHA-256 upon ingestion (`StorageService.computeSha256`).
* The hash is recorded in both the database (`artifact_versions.content_hash`) and the storage layer.
* **On Every Retrieval**: `LocalStorageService.retrieve()` re-computes the SHA-256 hash of the on-disk file and compares it in constant time against the expected database checksum.
* If any external process, disk corruption, or malicious actor modifies the file on disk:
  1. Retrieval immediately halts.
  2. An alert log is emitted: `INTEGRITY COMPROMISED: Storage key ... expected SHA-256 ... but found ...`.
  3. An `ApiException(HttpStatus.INTERNAL_SERVER_ERROR)` is thrown, preventing corrupt or tampered data from feeding RAG chunking or AI prompts.

### 3.2 Immutability Guarantees
* Under normal operating conditions, database triggers `trg_immutable_artifact_versions` and `trg_immutable_timeline_events` raise a fatal exception on any `UPDATE` or `DELETE`.
* Direct updates to historical artifacts or timeline events are prohibited at the database engine level.

---

## 4. Project Data Export Service (Data Portability)

* **Endpoint**: `GET /api/v1/projects/{projectId}/export`
* **Access Control**: Requires authenticated user with `VIEWER`, `MEMBER`, or `OWNER` project membership.
* **Archive Format**: Structured JSON archive containing:
  - `project`: Complete project metadata and configuration.
  - `artifacts`: List of all artifacts, including full version histories, storage keys, and author metadata.
  - `decisions`: Complete Architecture Decision Records (ADRs) with version progression and rationale.
  - `timelineEvents`: Chronological audit of platform, deployment, and agent interactions.
  - `tasks`: All workflow tasks and lifecycle states.
  - `actionRequests`: Atomic action proposals, human approvals, and execution results.
  - `exportedAt` / `exportedBy`: Provenance metadata.

---

## 5. Cascading Deletion & Tenant Purge (Right to be Forgotten)

* **Endpoint**: `DELETE /api/v1/projects/{projectId}`
* **Access Control**: Strictly restricted to project `OWNER`.
* **Confirmation Safety**: Requires request body `{ "confirmationCode": "CONFIRM_DELETE_<publicId>" }`. Any mismatch is rejected with `HTTP 400 Bad Request`.
* **Purge Execution Architecture**:
  1. Transactional bypass for immutability triggers:
     ```sql
     SET LOCAL aether.allow_project_purge = 'on';
     ```
  2. Single transactional `DELETE FROM projects WHERE id = ?`.
  3. Relational `ON DELETE CASCADE` purges all related tables:
     - `artifacts`, `artifact_versions`, `artifact_chunks`, `embeddings`
     - `technical_decisions`, `decision_versions`, `decision_evidence`
     - `timeline_events`, `audit_events`
     - `tasks`, `task_events`, `task_links`, `agent_sessions`
     - `action_requests`, `integration_checkpoints`, `mcp_tokens`
  4. Disk cleanup: `storageService.deleteProjectData(projectId)` recursively deletes the project directory on disk.

---

## 6. Retention Policies

| Data Category | Retention Model | Purge Method |
| :--- | :--- | :--- |
| **Active Project Memory** | Indefinite (immutable version history) | Project Archive (soft delete) or Cascading Purge (hard delete) |
| **Soft-Deleted Projects** | Hidden from active queries (`archived = true`) | Restorable by Project Owner; exportable at any time |
| **Audit Logs (`audit_events`)** | 365-day compliance retention | Retained for system security audits; purged upon tenant deletion |
| **Webhook Delivery Cache** | 7-day rolling window | Automatic expiry based on `received_at` index |

