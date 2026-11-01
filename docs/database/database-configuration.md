# Aether Database Configuration Reference

This guide details Aether's environment-based datasource configuration, supporting both local Docker PostgreSQL and Neon Serverless PostgreSQL in production.

---

## 1. Environment Variables Specification

Aether uses environment variables to configure its database connections. In production, these variables override application defaults without requiring code changes.

| Environment Variable | Required | Description | Example Value |
| :--- | :---: | :--- | :--- |
| `DB_URL` | **Yes** (Prod) | PostgreSQL JDBC Connection URL | `jdbc:postgresql://ep-xyz.us-east-2.aws.neon.tech:5432/neondb?sslmode=require` |
| `DB_USERNAME` | **Yes** (Prod) | PostgreSQL Database Username | `aether_neon_user` |
| `DB_PASSWORD` | **Yes** (Prod) | PostgreSQL Database Password | *(Secret password)* |
| `DB_MAX_POOL_SIZE` | No | Maximum HikariCP connection pool size | `10` (Default: `10` for Neon serverless) |
| `DB_MIN_IDLE` | No | Minimum idle connections | `2` (Default: `2`) |
| `DB_CONNECTION_TIMEOUT` | No | Timeout waiting for connection (ms) | `20000` (Default: 20s to absorb Neon cold starts) |
| `DB_IDLE_TIMEOUT` | No | Idle connection retention time (ms) | `300000` (Default: 5 min) |
| `DB_MAX_LIFETIME` | No | Maximum lifetime of a connection (ms) | `900000` (Default: 15 min) |

---

## 2. Supported Connection URL Patterns

Aether's built-in `DatabaseUrlEnvironmentPostProcessor` handles multiple cloud connection formats:

### A. Neon Direct JDBC Connection (Standard)
```
jdbc:postgresql://ep-silent-moon-123456.us-east-2.aws.neon.tech:5432/neondb?sslmode=require
```

### B. Neon Pooled JDBC Connection (PgBouncer)
Neon provides a connection pooler endpoint to handle large numbers of transient clients:
```
jdbc:postgresql://ep-silent-moon-123456-pooler.us-east-2.aws.neon.tech:5432/neondb?sslmode=require
```

### C. Standard PostgreSQL URI
If provided with a raw PostgreSQL URI, Aether automatically parses credentials and normalizes the scheme:
```
postgresql://aether_user:secret_pass@ep-silent-moon-123456.us-east-2.aws.neon.tech:5432/neondb
```
- Converts `postgresql://` to `jdbc:postgresql://`
- Automatically appends `sslmode=require` for `*.neon.tech` endpoints
- Extracts `username` and `password` if not explicitly passed via `DB_USERNAME` / `DB_PASSWORD`

---

## 3. Local Development vs. Production Profiles

### Local Development (Default / Docker)
When running locally with Docker (`docker-compose.yml`):
- No environment variables are required.
- Spring Boot falls back to:
  - URL: `jdbc:postgresql://localhost:5432/aether`
  - Username: `aether`
  - Password: `aether_dev_password`

### Production Profile (`SPRING_PROFILES_ACTIVE=prod`)
When running on Render or a cloud VM:
- The environment variables `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` must be set in the cloud platform's dashboard.
- TLS is mandatory (`sslmode=require`).
- Passwords are strictly masked in all application logs and actuator health check outputs.

---

## 4. HikariCP Tuning for Neon Serverless

Neon computes scale dynamically and can suspend idle compute nodes. The following HikariCP settings configured in `application.yml` ensure robust connectivity:

1. **`connection-timeout: 20000` (20s)**:
   Allows sufficient headroom for Neon compute instances to resume from suspended state ("cold start") without throwing client-side connection timeout exceptions.
2. **`max-lifetime: 900000` (15 min)**:
   Gracefully retires connections before intermediate cloud network proxies or stateful firewalls drop silent TCP connections.
3. **`idle-timeout: 300000` (5 min)**:
   Releases unused connections back to the database, preventing connection pool exhaustion on serverless tiers.
4. **`connection-test-query: SELECT 1`**:
   Actively verifies connection liveness before delivering connections from the pool.
