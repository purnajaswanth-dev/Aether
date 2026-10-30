# Phase 1 Implementation Plan: Foundation, Core Memory and Design System

## 1. Executive Summary & Architecture Overview
Phase 1 delivers the production-ready foundation of the Unified Engineering Context Platform:
- **Repository Architecture**: A professional monorepo with `/backend` (Spring Boot 3.3.5, Java 17 LTS, Maven, Spring JDBC, Flyway), `/frontend` (React 18, Vite, TypeScript, Tailwind CSS, Radix UI), `/docs` (Constitution, Design System, Architecture Plans), and `/scripts` (Docker Compose with PostgreSQL 16 + pgvector, initialization scripts).
- **Core Memory & Immutability**: Append-only storage for artifact versions and timeline events enforced by PostgreSQL database triggers. Content hash integrity (SHA-256) on write and read.
- **Tenant Isolation**: PostgreSQL Row-Level Security (RLS) on all project-scoped tables keyed on `app.current_project_id` plus service-layer checks.
- **Technical Decisions**: Strict lifecycle state machine (`PROPOSED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `SUPERSEDED`, `IMPLEMENTED`, `VERIFIED`) with optimistic concurrency locking. AI/Agent actors are strictly forbidden from approving decisions.
- **User Interface**: Faithful implementation of the calm, dense Linear/GitHub-inspired design system tokens, Radix UI primitives, split-pane views, diff viewer, timeline, command palette (`Cmd/Ctrl+K`), and a visual `/design` token showcase.

---

## 2. Directory Structure & Monorepo Organization
```
Aether/
├── backend/
│   ├── src/main/java/com/context_platform/aether/
│   │   ├── auth/          # User registration, login, Argon2id/BCrypt, session/token management, rate limiting
│   │   ├── project/       # Project creation, listing, membership, integrations catalog initialization
│   │   ├── artifact/      # Artifacts, append-only versions, diff calculation, search
│   │   ├── decision/      # Technical decisions, state machine, optimistic locking, version history
│   │   ├── storage/       # StorageService interface, local filesystem implementation, SHA-256 checks
│   │   ├── event/         # Timeline events, audit logging
│   │   ├── security/      # Spring Security configuration, RLS transaction context helper, ProjectSecurityChecker
│   │   └── common/        # Exceptions, global error handler, Base DTOs
│   ├── src/main/resources/
│   │   ├── db/migration/  # Flyway SQL migrations (V1 schema, V2 triggers, V3 RLS)
│   │   └── application.yml
│   ├── src/test/java/com/context_platform/aether/
│   │   ├── trigger/       # Tests asserting direct SQL UPDATE/DELETE fails on immutable tables
│   │   ├── rls/           # Tests proving zero cross-project leakage under RLS
│   │   ├── decision/      # State machine transition & concurrent optimistic locking tests
│   │   ├── artifact/      # Hash integrity & version diff tests
│   │   └── auth/          # Authentication, authorization, and actor permission tests
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── components/    # Design system primitives: Button, Input, Select, Dialog, Dropdown, Table, StatusPill, etc.
│   │   ├── layout/        # AppLayout, Sidebar (240px), TopBar, Breadcrumbs, CommandPalette
│   │   ├── pages/         # Welcome, CreateProject, Dashboard, Memory, Decisions, Timeline, Artifacts, Settings, DesignShowcase
│   │   ├── services/      # API client hooks via TanStack Query
│   │   ├── types/         # TypeScript domain models and API contracts
│   │   └── index.css      # CSS variables for all design tokens, Tailwind directives
│   ├── index.html
│   ├── package.json
│   ├── vite.config.ts
│   ├── tailwind.config.js
│   └── playwright.config.ts
├── docs/
│   ├── CONSTITUTION.md
│   ├── DESIGN_SYSTEM.md
│   ├── plans/phase-1.md
│   └── phase-1-report.md
├── scripts/
│   └── setup.sh / setup.bat
├── docker-compose.yml     # PostgreSQL 16 with pgvector (pgvector/pgvector:pg16)
└── README.md
```

---

## 3. Database Schema & Security Boundaries (PostgreSQL 16 + pgvector)

### Tables
1. `users`: `id` (UUID), `email` (citext/varchar unique), `password_hash`, `display_name`, `created_at`, `updated_at`.
2. `projects`: `id` (UUID), `public_id` (VARCHAR e.g. `PRJ-001`), `name`, `description`, `project_type`, `tech_stack` (JSONB), `directory`, `storage_preference`, `archived`, `created_at`, `updated_at`.
3. `project_members`: `id`, `project_id`, `user_id`, `role` (`OWNER`, `MEMBER`, `VIEWER`), `created_at`.
4. `project_integrations`: `id`, `project_id`, `provider_id` (`claude`, `chatgpt`, `antigravity`, `github`, `render`, `postman`, `docker`), `status` (defaults to `NOT_CONNECTED`), `config` (JSONB), `created_at`, `updated_at`.
5. `project_permissions`: `id`, `project_id`, `integration_id`, `permission` (6 constitutional permissions), `granted`, `created_at`.
6. `artifacts`: `id`, `project_id`, `title`, `source_platform`, `source_reference`, `artifact_type`, `related_task_id`, `created_at`.
7. `artifact_versions`: `id`, `artifact_id`, `project_id`, `version`, `content`, `storage_key`, `content_hash` (SHA-256), `byte_size`, `created_by`, `actor_type`, `created_at`.
8. `artifact_references`: `id`, `project_id`, `source_artifact_id`, `target_artifact_id`, `reference_type`, `created_at`.
9. `technical_decisions`: `id`, `project_id`, `public_id` (e.g. `DEC-001`), `title`, `state`, `category`, `current_version`, `lock_version` (INTEGER for optimistic locking), `created_at`, `updated_at`.
10. `decision_versions`: `id`, `decision_id`, `project_id`, `version`, `state`, `problem_statement`, `proposed_solution`, `rationale`, `consequences`, `source_artifact_version_id`, `actor_id`, `actor_type`, `review_notes`, `created_at`.
11. `decision_evidence`: `id`, `decision_id`, `project_id`, `evidence_type`, `description`, `artifact_version_id`, `created_by`, `created_at`.
12. `timeline_events`: `id`, `project_id`, `task_id`, `source_platform`, `event_type`, `associated_artifact_id`, `associated_decision_id`, `decision_version`, `status`, `actor_id`, `actor_type`, `summary`, `metadata` (JSONB), `created_at`.
13. `audit_events`: `id`, `project_id`, `user_id`, `action`, `actor_type`, `ip_address`, `user_agent`, `details` (JSONB), `created_at`.

### Immutability Triggers
- PostgreSQL trigger function `reject_update_delete()` attached to `artifact_versions` and `timeline_events`.
- Any `UPDATE` or `DELETE` statement raises an exception: `"Table % is immutable. UPDATE and DELETE are forbidden."`

### Row-Level Security (RLS)
- Enabled and forced on all project-scoped tables:
  `USING (project_id = NULLIF(current_setting('app.current_project_id', true), '')::uuid)`
- Repository RlsContext helper sets `SET LOCAL app.current_project_id = ?` at transaction start.

---

## 4. REST API Contracts

### Authentication (`/api/v1/auth`)
- `POST /register`: `{ email, password, displayName }` -> `{ user, token }`
- `POST /login`: `{ email, password }` -> `{ user, token }`
- `GET /me`: returns current authenticated user profile
- `POST /logout`: invalidates session

### Projects (`/api/v1/projects`)
- `POST /`: `{ name, description, projectType, techStack, directory, storagePreference }` -> Creates project, initial memory namespace, registers all 7 catalogue integrations with `NOT_CONNECTED`, logs timeline event, returns created project.
- `GET /`: Lists projects accessible to current user.
- `GET /{id}`: Gets project details.
- `PUT /{id}`: Updates project info.
- `POST /{id}/archive`: Archives project.

### Artifacts (`/api/v1/projects/{projectId}/artifacts`)
- `POST /`: `{ title, artifactType, sourcePlatform, sourceReference, content }` -> Stores version 1, computes SHA-256 hash, emits timeline event.
- `POST /{artifactId}/versions`: `{ content, changeSummary }` -> Stores version N+1, computes SHA-256 hash.
- `GET /`: Filterable by title, artifactType, sourcePlatform.
- `GET /{artifactId}/latest`: Returns latest version with content and hash.
- `GET /{artifactId}/versions/{version}`: Returns specific version.
- `GET /{artifactId}/versions`: Lists all version metadata.
- `GET /{artifactId}/compare?v1={v1}&v2={v2}`: Returns structured line-by-line diff.

### Technical Decisions (`/api/v1/projects/{projectId}/decisions`)
- `POST /`: `{ title, category, problemStatement, proposedSolution, rationale, consequences, sourceArtifactVersionId }` -> Creates decision in `PROPOSED` state (Actor: HUMAN_USER or AGENT).
- `GET /`: Lists decisions with state and category filters.
- `GET /{decisionId}`: Returns decision details with version history.
- `POST /{decisionId}/review`: Submits for review (`PROPOSED` -> `UNDER_REVIEW`).
- `POST /{decisionId}/approve`: Requires `HUMAN_USER`. Optimistic lock verified via `lock_version`.
- `POST /{decisionId}/reject`: Requires `HUMAN_USER`. Reason required.
- `POST /{decisionId}/supersede`: Requires `HUMAN_USER`. Links superseding decision.
- `GET /{decisionId}/compare`: Side-by-side comparison (proposal, review findings, modified proposal, final approved).

### Timeline & Audit (`/api/v1/projects/{projectId}/timeline`)
- `GET /`: Returns chronological events with platform, actor, artifact/decision links, filterable by eventType and platform.

---

## 5. StorageService Specification
```java
public interface StorageService {
    StorageResult store(UUID projectId, String filename, byte[] content);
    byte[] retrieve(UUID projectId, String storageKey, String expectedHash);
    void verifyIntegrity(UUID projectId, String storageKey, String expectedHash);
}
```
- For content <= 256 KB: stored inline in DB and verified against SHA-256.
- For content > 256 KB: stored in local storage directory (and in DB as backup reference), verified on read and write.

---

## 6. Frontend Component Architecture & Design System
- **Design Tokens**: Defined in CSS variables + Tailwind theme:
  - Canvas: `#FFFFFF`, Subtle: `#F7F8FA`, Muted: `#EFF1F4`, Border: `#E3E6EA`, Border-strong: `#CDD2D9`
  - Text: Primary `#1B1F24`, Secondary `#5B6470`, Tertiary `#8A93A0`
  - Accent: `#4F5BD5`, Hover `#4350C4`, Subtle `#EEF0FD`
  - Status: Success `#1F8A4C`, Warning `#B26A00`, Danger `#D0343B`, Info `#1F6FEB`
- **Component Primitives**:
  - `Button`, `Input`, `Select`, `Dialog`, `DropdownMenu`, `Tabs`, `Table`, `Badge/StatusPill`, `Tooltip`, `Toast`, `Skeleton`, `EmptyState`, `CommandPalette`, `Sidebar`, `Breadcrumbs`, `SplitPane`, `DiffViewer`, `CodeBlock`.
- **Views**:
  - `/welcome`: First run onboarding, no fake dashboards, links to documentation & settings.
  - `/projects/new`: Create Project wizard with validation.
  - `/projects/:id/overview`: Project overview with real data.
  - `/projects/:id/memory`: Shared memory organized by constitutional categories.
  - `/projects/:id/decisions`: Decisions with lifecycle state pills, version history, compare view, approval buttons.
  - `/projects/:id/artifacts`: List-detail split pane, original content viewer with hash display, copy button, version comparison.
  - `/projects/:id/timeline`: Chronological event trail linking to artifacts/decisions.
  - `/projects/:id/integrations`: Honest catalogue with 7 providers, all displaying "Not connected" / "Not available yet" with planned capabilities.
  - `/projects/:id/settings`: Project info, 6-permission matrix.
  - `/design`: Comprehensive showcase of every design system component.

---

## 7. Verification Gate Checklist (Phase 1 Acceptance Criteria)
1. User registration, project creation, storing a 50 KB architecture doc, version 2 creation, and version comparison (v1 remains byte-identical with verified SHA-256 hash).
2. Direct SQL `UPDATE` / `DELETE` on `artifact_versions` and `timeline_events` fails via triggers.
3. User A cannot access User B's project via API or via repository calls under RLS.
4. Agent-typed actor cannot approve a decision.
5. Two concurrent approvals: exactly one succeeds via optimistic locking.
6. Timeline contains an event for every state change with all required fields.
7. No fake data or simulated integration status exists in production code paths.
8. Clean builds for Maven and npm, all tests passing.
