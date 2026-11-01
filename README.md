# Aether — Unified Engineering Context Platform

**Aether** is a project-centric persistent memory, context-orchestration, and workflow-coordination platform for modern engineering teams and autonomous AI agents (Claude, ChatGPT, Google Antigravity, GitHub, Render, Postman, Docker).

---

## 🚀 Key Capabilities

* **Architectural Memory & Artifact Immutability**: Store specifications, architecture decision records (ADRs), and schemas with cryptographic SHA-256 verification and PostgreSQL immutability triggers.
* **Remote Model Context Protocol (MCP) Server**: Connect independent AI agents via SSE or Streamable HTTP transports with fine-grained capability tokens, instant revocation, pause/resume, and token rotation.
* **Local In-Process Semantic Search & Hybrid Retrieval**: In-memory Microsoft ONNX Runtime (`all-MiniLM-L6-v2`, 384 dimensions) combined with PostgreSQL `pgvector` and BM25 Reciprocal Rank Fusion (RRF) delivers sub-25ms hybrid queries without external embedding API costs.
* **Token-Budgeted Context Orchestration**: Assemble model-tailored context packages with strict token ceilings, status-aware re-ranking, and structured fact extraction.
* **Workflow Coordination & Evidence Gating**: Strict task state machine preventing AI agents from claiming `VERIFIED` status without qualifying passing test execution evidence or explicit human override.
* **Human-in-the-Loop Approval Inbox**: Safe gating for destructive agent actions (deployments, PR mergers) with JSON parameter inspection, double-click debounce, and explicit `CONFIRM` typing verification.
* **Production Observability & Enterprise Privacy**: Correlation ID tracing (`X-Correlation-ID`) in MDC and response headers, custom Micrometer metrics, Actuator healthcheck, full project JSON archive export, and GDPR cascading purge.

---

## 🏗️ Architecture & Monorepo Structure

```text
├── backend/                  # Spring Boot 3.3.5 (Java 17 LTS), Maven, PostgreSQL 16 + pgvector, ONNX Runtime
│   ├── src/main/java/        # Modular monolith (auth, artifact, decision, ingestion, retrieval, mcp, workflow, data, observability)
│   ├── src/main/resources/   # Flyway migrations (V1–V10), ONNX model, logback configuration
│   └── Dockerfile            # Multi-stage container build (Temurin 17 JRE, non-root user UID 10001)
├── frontend/                 # React 18, TypeScript, Vite, Tailwind CSS, Radix UI, TanStack Query, Lucide
│   ├── src/                  # Web application, approval inbox, shared memory, living design system
│   ├── nginx.conf            # Hardened Nginx reverse proxy with CSP, HSTS, X-Frame-Options: DENY
│   └── Dockerfile            # Multi-stage production build (Node 20 -> Nginx 1.27 Alpine)
├── agent/                    # High-performance Local Agent CLI daemon written in Go
│   └── ...                   # Codebase scanner, git commit collector, AST parser
├── docs/                     # Platform Constitution, Design System, STRIDE Threat Model, Verification Reports (Phases 1-6)
├── docker-compose.yml        # Local development PostgreSQL 16 + pgvector
├── docker-compose.prod.yml   # Full containerized production stack (PostgreSQL, Backend, Frontend)
└── .env.example              # Production configuration template
```

---

## 🛠️ Prerequisites

* **Java 17 LTS** (Adoptium Eclipse Temurin or equivalent)
* **Apache Maven 3.9+**
* **Node.js 20+** & **npm**
* **Go 1.22+** (optional, for local agent CLI)
* **Docker Engine 24+** & **Docker Compose v2+**

---

## ⚡ Quickstart: Local Development

### 1. Start PostgreSQL 16 + pgvector
```bash
docker compose up -d postgres
```

### 2. Start Backend API (Spring Boot on port 8080)
```bash
cd backend
mvn spring-boot:run
```
* **Swagger / OpenAPI Documentation**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
* **Actuator Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

### 3. Start Frontend Dev Server (React on port 5173)
```bash
cd frontend
npm install
npm run dev
```
* **Web Application**: [http://localhost:5173](http://localhost:5173)
* **Design System Showcase**: [http://localhost:5173/design](http://localhost:5173/design)

---

## 🐳 Production Deployment (Docker Compose)

1. Create production environment secrets:
   ```bash
   cp .env.example .env
   ```
2. Build and launch all production services:
   ```bash
   docker compose -f docker-compose.prod.yml up --build -d
   ```
3. Verify health status:
   ```bash
   docker compose -f docker-compose.prod.yml ps
   ```
   Access the production portal at **`http://localhost`** (port 80).

---

## 🚀 Cloud Deployment (Render Blueprint)

Aether includes native Infrastructure as Code for **1-click deployment on [Render](https://render.com)** via [`render.yaml`](file:///c:/Users/sunka/OneDrive/coding/Aether/render.yaml):

1. Push your repository to GitHub.
2. In the [Render Dashboard](https://dashboard.render.com), click **New +** > **Blueprint**.
3. Select your repository and click **Apply**.
4. Render provisions:
   - **`aether-db`**: Managed PostgreSQL 16 with `pgvector`
   - **`aether-backend`**: Spring Boot 3.3.5 Docker container with health checks and 10GB persistent disk
   - **`aether-frontend`**: React + Vite SPA on global CDN with SPA rewrites
   - Auto-generated 256-bit secrets for JWT authentication and credential encryption

For detailed step-by-step instructions, see the complete [Render Deployment Guide](file:///c:/Users/sunka/OneDrive/coding/Aether/docs/deployment/render.md).

---

## 🧪 Running Automated Test Suites

### Backend Tests (JUnit 5 + Spring Boot + pgvector)
```bash
cd backend
mvn test
```
To run the full Phase 6 production hardening and acceptance suite:
```bash
cd backend
mvn test "-Dtest=Phase6ProductionAcceptanceTest,AdversarialCrossProjectSecurityTest,McpTokenHardeningTest,PromptInjectionDefenseTest,ReliabilityAndJobRecoveryTest,PerformanceBenchmarkTest,DataProtectionTest,ObservabilityTest"
```

### Frontend Tests (Vitest) & Production Build Check
```bash
cd frontend
npm run test:run
npm run build
```

### Local Agent Go Tests
```bash
cd agent
go test ./...
```

---

## 📜 Constitutional Principles

1. **Evidence Over Assertion**: Claims of completion or correctness require recorded evidence (passing test executions, verified artifacts, or explicit human justification).
2. **Strict Actor Governance**: AI agents may propose tasks and architectural decisions; only authenticated human users can approve decisions or authorize destructive actions.
3. **Data Integrity & Immutability**: Historical events and artifact versions cannot be modified or deleted directly in the database.
4. **Product Honesty**: No simulated statuses or fake integration cards; unsupported or planned providers truthfully display their actual connection status.
5. **Zero Tenant Leakage**: Strict project isolation enforced at both the application and database (Row-Level Security) levels.
