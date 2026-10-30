# Project Memory: Unified Engineering Context Platform (Aether)

## Current Status
- **Current Phase**: Phase 1 - Foundation, Core Memory and Design System [COMPLETED & VERIFIED]
- **Timestamp**: 2026-10-31
- **Outcome**: A developer can register, create a project, store complete architecture documents (50+ KB) with SHA-256 verification, create immutable versions, run structured diffs, propose and review technical decisions, approve decisions with human-only checks and optimistic locking, observe the append-only timeline, and navigate the calm, professional engineering UI shell.

## Architecture & Technology Decisions
- **Monorepo Layout**:
  - `/backend`: Spring Boot 3.3.5 (Java 17 LTS), Maven, Spring JDBC (`JdbcClient`), Flyway migrations (V1 initial schema, V2 immutability triggers, V3 RLS, V4 tenant role), PostgreSQL 16 + pgvector, Argon2id, BouncyCastle, jjwt, java-diff-utils.
  - `/frontend`: React 18, Vite, TypeScript, Tailwind CSS, Radix UI primitives, TanStack Query, Lucide icons, Vitest, Playwright.
  - `/docs`: Constitution (`CONSTITUTION.md`), Design System (`DESIGN_SYSTEM.md`), Phase 1 Plan (`plans/phase-1.md`), Phase 1 Report (`phase-1-report.md`).
  - `/scripts`: Setup scripts (`setup.sh`, `setup.bat`).
  - `docker-compose.yml`: PostgreSQL 16 with pgvector (`pgvector/pgvector:pg16`).
- **Data Integrity & Immutability**:
  - Append-only `artifact_versions` and `timeline_events` enforced by PostgreSQL triggers rejecting `UPDATE` and `DELETE`.
  - SHA-256 hash verified on both write and read. Inline content <= 256 KB, filesystem storage for large files with `StorageService`.
  - PostgreSQL Row-Level Security (RLS) forced on all project-scoped tables with tenant role `aether_app` (`NOBYPASSRLS`).
- **Decision State Machine & Optimistic Locking**:
  - Lifecycle: `PROPOSED`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `SUPERSEDED`, `IMPLEMENTED`, `VERIFIED`.
  - `AGENT` actors cannot approve decisions. Authenticated human session strictly required.
  - Optimistic locking via `lock_version` prevents race conditions.
- **Design System & UI**:
  - Light theme tokens implemented as CSS variables and Tailwind theme.
  - Linear/GitHub dense engineering aesthetic: 36px table rows, 4px grid, 6px/8px radii, subtle borders, skeleton loaders, command palette (`Cmd/Ctrl+K`), and hidden `/design` preview route.

## Verification Evidence (Phase 1 Gate Checklist)
- [x] **Gate 1**: 50+ KB architecture doc stored (v1), version 2 created with structured diff; version 1 remains 100% byte-identical (hash verified).
- [x] **Gate 2**: Direct SQL `UPDATE` and `DELETE` on `artifact_versions` and `timeline_events` fail via database triggers.
- [x] **Gate 3**: User A cannot access User B's project via API or via repository calls under RLS (0 rows visible, cross-project inserts blocked).
- [x] **Gate 4**: Agent-typed actor cannot approve a decision; human reviewer approval succeeds.
- [x] **Gate 5**: Two concurrent approvals on the same decision: exactly ONE succeeds, the other fails safely with `OptimisticLockingException`.
- [x] **Gate 6**: Timeline contains an event for every state change with all required fields.
- [x] **Gate 7**: No fake data or simulated integration status exists in production code paths (providers truthfully display `NOT_CONNECTED`).
- [x] **Gate 8**: All 9 backend tests pass in Maven; all 6 frontend tests pass in Vitest; frontend builds cleanly. Setup commands verified.
