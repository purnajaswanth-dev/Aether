# Docker PostgreSQL to Neon PostgreSQL Migration Procedure

This runbook describes the exact steps to migrate data from the local Docker PostgreSQL database to Neon PostgreSQL.

> [!IMPORTANT]
> **Safety Rule**: Never run destructive migration commands without explicit approval. The local Docker database must remain untouched as an authoritative backup.

---

## 1. Prerequisites

Before starting:
1. Docker must be running with the `aether-postgres` container active.
2. Neon credentials must be ready:
   - Host (e.g. `ep-cool-butterfly-123456.us-east-2.aws.neon.tech`)
   - Database name (e.g. `neondb` or `aether`)
   - Username (e.g. `aether_admin`)
   - Password

---

## 2. Step 1: Export Local Docker Database

Run the export script. This performs a **read-only** dump using PostgreSQL custom format (`-Fc`) with `--no-owner --no-acl`.

### On Linux / macOS:
```bash
chmod +x scripts/dump-docker-db.sh
./scripts/dump-docker-db.sh
```

### On Windows (PowerShell):
```powershell
.\scripts\dump-docker-db.ps1
```

### Output:
The dump file will be created at:
- `data/backups/aether_docker_<timestamp>.dump`
- `data/backups/aether_docker_latest.dump` (pointer to the most recent backup)

Verify that the backup file size is greater than 0 bytes before proceeding.

---

## 3. Step 2: Restore to Neon PostgreSQL

Once you have verified the dump file and have your Neon credentials:

### On Linux / macOS:
```bash
chmod +x scripts/restore-neon-db.sh
DB_URL="postgresql://<USER>:<PASSWORD>@<NEON_HOST>/<DATABASE>?sslmode=require" ./scripts/restore-neon-db.sh
```

### On Windows (PowerShell):
```powershell
$env:DB_URL = "postgresql://<USER>:<PASSWORD>@<NEON_HOST>/<DATABASE>?sslmode=require"
.\scripts\restore-neon-db.ps1
```

The script will display the target host and require you to type **`CONFIRM`** before any data is written to Neon.

---

## 4. Step 3: Run Post-Migration Verification

Verify the restored database using `psql` (or Neon SQL Editor):

```bash
psql "$DB_URL" -f scripts/verify-neon-migration.sql
```

The script executes 5 verification suites:
1. **Extensions**: Confirms `uuid-ossp`, `vector`, and `pg_trgm` are installed.
2. **Flyway History**: Confirms all 10 schema migrations (`V1` to `V10`) are present.
3. **Table Counts**: Reports record counts across all core tables (`users`, `projects`, `artifacts`, `decisions`, `tasks`, `embeddings`, etc.).
4. **Referential Integrity**: Verifies zero orphaned versions, chunks, or embeddings.
5. **Functional Query Tests**: Runs live tests for Full-Text Search, Trigram similarity, and pgvector cosine distance (`<=>`).

---

## 5. Step 4: Configure Render Web Service

Once verification passes:
1. Open your [Render Dashboard](https://dashboard.render.com).
2. Navigate to your `aether-backend` Web Service.
3. In **Environment**, configure:
   - `DB_URL`: `jdbc:postgresql://<NEON_HOST>:5432/<DATABASE>?sslmode=require`
   - `DB_USERNAME`: `<NEON_USER>`
   - `DB_PASSWORD`: `<NEON_PASSWORD>`
4. Click **Save Changes** and trigger a manual deployment.
5. Monitor runtime logs to confirm successful startup:
   ```
   HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection@...
   Started AetherApplication in ... seconds
   ```

