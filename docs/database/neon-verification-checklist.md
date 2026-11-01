# Aether — Neon Verification Checklist

Use this checklist to track and verify the complete migration from Docker PostgreSQL to Neon PostgreSQL.

---

## 1. Pre-Flight Preparation Checklist

- [x] Initial database architecture and hardcoded coordinates audited.
- [x] Spring Boot datasource configured for `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
- [x] `DatabaseUrlEnvironmentPostProcessor` implemented with automatic TLS (`sslmode=require`) enforcement.
- [x] HikariCP connection pool tuned for serverless cold starts and proxy lifecycles.
- [x] Safe data export script prepared (`scripts/dump-docker-db.sh`, `scripts/dump-docker-db.ps1`).
- [x] Interactive data restore script prepared (`scripts/restore-neon-db.sh`, `scripts/restore-neon-db.ps1`).
- [x] Post-migration SQL verification script prepared (`scripts/verify-neon-migration.sql`).
- [x] Local Docker database intact and preserved.
- [ ] Neon project created on [neon.tech](https://neon.tech).
- [ ] Neon connection string and credentials received from user.

---

## 2. PostgreSQL Feature & Extension Compatibility Matrix

| Feature / Extension | Aether Usage | Neon Support Status | Verification Method |
| :--- | :--- | :---: | :--- |
| **`pgvector`** | 384-dim semantic embeddings, HNSW index, cosine distance (`<=>`) | **Fully Supported** | Verified pgvector 0.5+ native support in Neon |
| **`pg_trgm`** | Trigram similarity for fuzzy code search (`similarity()`) | **Fully Supported** | Built-in extension in Neon |
| **`uuid-ossp`** | Legacy UUID generation (`uuid_generate_v4()`) | **Fully Supported** | Built-in extension in Neon |
| **Full-Text Search** | Generated stored column `tsv tsvector`, `to_tsvector()`, GIN index | **Fully Supported** | Core PostgreSQL standard feature |
| **JSONB Operations** | Metadata storage, tech stack arrays, task parameters | **Fully Supported** | Core PostgreSQL standard feature |
| **Row-Level Security** | Multitenant project isolation (`FORCE ROW LEVEL SECURITY`) | **Fully Supported** | Core PostgreSQL standard feature |
| **Flyway Migrations** | Schema versioning (`V1` to `V10`) | **Fully Supported** | Standard Flyway PostgreSQL support |

---

## 3. Post-Restore Verification Checklist

*(Execute after restoring dump to Neon)*

- [ ] Run `scripts/verify-neon-migration.sql` against the Neon database.
- [ ] Verify `uuid-ossp`, `vector`, and `pg_trgm` extensions exist in `pg_extension`.
- [ ] Verify 10 migration rows in `flyway_schema_history` with `success = true`.
- [ ] Verify user record count matches local Docker database.
- [ ] Verify project record count matches local Docker database.
- [ ] Verify artifact version count matches local Docker database.
- [ ] Verify chunk embedding count matches local Docker database.
- [ ] Confirm `orphaned_artifact_versions` = 0.
- [ ] Confirm `orphaned_embeddings` = 0.
- [ ] Confirm Full-Text Search test status = `PASSED`.
- [ ] Confirm `pg_trgm` similarity test status = `PASSED`.
- [ ] Confirm `pgvector` distance test status = `PASSED`.

---

## 4. Render Deployment Checklist

- [ ] Render Web Service configured with `DB_URL` (`sslmode=require`).
- [ ] Render Web Service configured with `DB_USERNAME`.
- [ ] Render Web Service configured with `DB_PASSWORD`.
- [ ] Render Web Service configured with `AETHER_STORAGE_LOCAL_PATH=/app/data/artifacts`.
- [ ] Render Web Service has 10 GB persistent disk mounted.
- [ ] Backend starts up cleanly with `HikariPool-1 - Added connection`.
- [ ] Health check endpoint `GET /actuator/health` returns `{"status":"UP"}`.
- [ ] MCP Server endpoint `POST /api/mcp` responds with valid JSON-RPC.
- [ ] Frontend successfully authenticates against the backend on Neon.
