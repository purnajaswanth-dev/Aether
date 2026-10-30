# Unified Engineering Context Platform (Aether)

A project-centric persistent memory and context-exchange layer for software teams.
One project, one persistent memory, many independent AI agents and engineering tools (ChatGPT, Claude, Google Antigravity, GitHub, Render, Postman, Docker).

---

## Architecture & Monorepo Structure

- `/backend`: Spring Boot 3.3.5, Java 17 LTS, Maven, Spring JDBC (`JdbcClient`), Flyway, PostgreSQL 16 with pgvector, Argon2id auth, immutable triggers, Row-Level Security (RLS).
- `/frontend`: React 18, Vite, TypeScript, Tailwind CSS, Radix UI primitives, TanStack Query, Lucide icons, Vitest, Playwright.
- `/docs`: Constitutional guidelines (`docs/CONSTITUTION.md`), Design System specifications (`docs/DESIGN_SYSTEM.md`), phase implementation plans, and phase reports.
- `/scripts`: Automated cross-platform setup scripts (`setup.sh`, `setup.bat`).
- `docker-compose.yml`: PostgreSQL 16 with pgvector (`pgvector/pgvector:pg16`).

---

## Prerequisites

- **Java 17 (LTS)**: Adoptium Temurin or equivalent
- **Apache Maven 3.9+**
- **Node.js 20+** and **npm**
- **Docker** and **Docker Compose**

---

## Setup & Local Development (Exact Working Commands)

### 1. Start the PostgreSQL 16 + pgvector Database
```bash
docker compose up -d
```
Verify the container is healthy:
```bash
docker compose ps
```

### 2. Start the Backend API (Spring Boot 3.3.5 on port 8080)
```bash
cd backend
mvn clean spring-boot:run
```
- OpenAPI Documentation / Swagger UI: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- Actuator Health Endpoint: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 3. Start the Frontend Dev Server (React 18 / Vite on port 5173)
```bash
cd frontend
npm install
npm run dev
```
- Web Application: [http://localhost:5173](http://localhost:5173)
- Living Design System Token Showcase: [http://localhost:5173/design](http://localhost:5173/design)

---

## Running the Automated Test Suites

### Backend Unit & Integration Tests (JUnit 5, Spring JDBC, PostgreSQL Triggers & RLS)
```bash
cd backend
mvn test
```
Tests assert:
- Immutability database triggers reject `UPDATE` and `DELETE` on `artifact_versions` and `timeline_events`.
- PostgreSQL Row-Level Security (RLS) guarantees zero cross-project leakage.
- AI Agents cannot approve decisions (only authenticated human users can approve).
- Concurrent approval attempts fail safely via optimistic concurrency locking.
- 50 KB architecture document storage maintains byte-identical SHA-256 integrity on write and read.
- Login brute-force rate limiter blocks accounts after 5 failed attempts.

### Frontend Component Tests (Vitest + React Testing Library)
```bash
cd frontend
npm run test:run
```

### Frontend Production Build
```bash
cd frontend
npm run build
```

---

## Constitutional Guarantees (Phase 1)
1. **Append-Only Immutability**: Historical events and artifact versions cannot be overwritten or deleted directly in the database.
2. **Strict Actor Governance**: AI agents may propose decisions; only authenticated human users can approve them.
3. **Optimistic Concurrency**: Technical decisions use atomic version checking to prevent race conditions.
4. **Product Honesty**: No simulated statuses or fake integration cards; unsupported or upcoming providers truthfully display "Not connected" / "Not available yet".
