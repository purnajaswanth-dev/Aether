# Aether Production Deployment Guide — Render Cloud

This guide provides end-to-end instructions for deploying the complete Aether Unified Engineering Context Platform to [Render](https://render.com).

---

## 1. Architecture Overview on Render

Aether deploys on Render using three core cloud components:

```mermaid
flowchart LR
    subgraph Render Cloud
        DB[("aether-db<br/>PostgreSQL 16 + pgvector")]
        Backend["aether-backend<br/>Docker Web Service<br/>Spring Boot 3.3.5"]
        Frontend["aether-frontend<br/>Static Site (Global CDN)<br/>React 18 + Vite"]
        Disk[("Persistent Disk<br/>/app/data/artifacts")]
    end

    Clients(["AI Clients & Agents<br/>Antigravity / Claude / ChatGPT / Cursor"])
    GitHub(["GitHub Webhooks"])
    LocalAgent(["Aether Local Agent (CLI)"])
    Browser(["Engineering Team (Browser)"])

    Browser -->|HTTPS| Frontend
    Frontend -->|REST API / JSON| Backend
    Backend -->|JDBC / RLS| DB
    Backend <-->|Storage| Disk
    Clients -->|MCP Streamable HTTP / SSE| Backend
    GitHub -->|Signed Webhooks| Backend
    LocalAgent -->|Pairing Code / Mutual Token| Backend
```

1. **`aether-db` (PostgreSQL 16 with `pgvector`)**: Stores user accounts, projects, RLS policies, tasks, decisions, embeddings (384-dim vector), and timeline events.
2. **`aether-backend` (Docker Web Service)**: Runs the modular Spring Boot monolith, handling REST APIs, MCP Server (`/api/mcp`), in-process ONNX embeddings, secret redaction, and integrations. Attached to a persistent disk for raw artifact storage.
3. **`aether-frontend` (Static Site on Global CDN)**: High-performance SPA with client-side routing, connected directly to `aether-backend`.

---

## 2. Option A: 1-Click Blueprint Deployment (Recommended)

Render Blueprints allow you to deploy the entire stack—Database, Backend, Frontend, Volumes, and Secrets—automatically from the [`render.yaml`](../../render.yaml) file in your repository.

### Prerequisites
1. A [Render account](https://dashboard.render.com).
2. Your Aether repository pushed to your GitHub or GitLab account.

### Step-by-Step Instructions

1. Log into your [Render Dashboard](https://dashboard.render.com).
2. Click **New +** in the top navigation bar and select **Blueprint**.
3. Connect your Git repository containing Aether.
4. Render will inspect `render.yaml` and display the blueprint plan:
   - **`aether-db`** (PostgreSQL 16)
   - **`aether-backend`** (Web Service, Docker)
   - **`aether-frontend`** (Static Site)
   - **`aether-artifact-storage`** (10 GB Persistent Disk)
5. Review the plan and click **Apply**.
6. Render will automatically:
   - Provision the PostgreSQL database with `pgvector`.
   - Generate cryptographically secure values for `AETHER_JWT_SECRET` and `AETHER_MASTER_KEY`.
   - Build the backend Docker image and mount the persistent disk.
   - Run Flyway migrations (`V1` through `V10`) automatically on first boot.
   - Build and publish the frontend SPA to Render's CDN.

---

## 3. Option B: Manual Step-by-Step Deployment

If you prefer to configure each service manually via the Render UI:

### Step 3.1: Provision PostgreSQL Database
1. Go to **New +** > **PostgreSQL**.
2. Set **Name**: `aether-db`.
3. Set **Database**: `aether`.
4. Set **User**: `aether`.
5. Set **PostgreSQL Version**: `16`.
6. Select **Plan**: `Starter` (or higher).
7. Click **Create Database**.
8. Once provisioned, copy the **Internal Database URL** (e.g. `postgresql://aether:password@dpg-xxx:5432/aether`).

### Step 3.2: Create Backend Web Service
1. Go to **New +** > **Web Service**.
2. Select your repository.
3. Configure the service:
   - **Name**: `aether-backend`
   - **Runtime**: `Docker`
   - **Dockerfile Path**: `backend/Dockerfile`
   - **Docker Context**: `backend`
   - **Instance Type**: `Starter` (1 CPU, 2 GB RAM recommended for ONNX embedding runtime)
   - **Health Check Path**: `/actuator/health`
4. Add **Persistent Disk**:
   - Under **Disks**, click **Add Disk**.
   - **Name**: `aether-artifact-storage`
   - **Mount Path**: `/app/data/artifacts`
   - **Size**: `10 GB`
5. Configure **Environment Variables**:
   | Variable | Value | Description |
   | :--- | :--- | :--- |
   | `SPRING_PROFILES_ACTIVE` | `prod` | Activates production profile |
   | `DATABASE_URL` | *(Paste Internal Database URL from Step 3.1)* | Automatically normalized to JDBC by Aether |
   | `AETHER_STORAGE_LOCAL_PATH` | `/app/data/artifacts` | Path to persistent disk mount |
   | `AETHER_JWT_SECRET` | *(64-character random hex string)* | Signs authentication JWT tokens |
   | `AETHER_MASTER_KEY` | *(64-character random hex string)* | AES-256 master key for third-party credentials |
   | `AETHER_CORS_ALLOWED_ORIGINS` | `http://localhost:*,https://*.onrender.com` | Allowed CORS origins |
6. Click **Create Web Service**.
7. Note down your backend URL (e.g., `https://aether-backend.onrender.com`).

### Step 3.3: Create Frontend Static Site
1. Go to **New +** > **Static Site**.
2. Select your repository.
3. Configure the site:
   - **Name**: `aether-frontend`
   - **Root Directory**: `frontend`
   - **Build Command**: `npm ci && npm run build`
   - **Publish Directory**: `dist`
4. Configure **Environment Variables**:
   - `VITE_API_URL`: `https://aether-backend.onrender.com` (your backend URL from Step 3.2)
   - `NODE_VERSION`: `20`
5. Configure **Redirects / Rewrites** (SPA Routing):
   - Under **Redirects/Rewrites**, click **Add Rule**.
   - **Type**: `Rewrite`
   - **Source**: `/*`
   - **Destination**: `/index.html`
6. Click **Create Static Site**.

---

## 4. Post-Deployment Verification

### 4.1 Verify Health Check
Open your browser or run curl against the backend health endpoint:
```bash
curl https://aether-backend.onrender.com/actuator/health
```
Expected response:
```json
{"status":"UP"}
```

### 4.2 Verify Database Schema Migrations
Check the backend runtime logs in Render Dashboard. You should see Flyway output:
```
Successfully applied 10 migrations to schema "public", now at version v10 (execution time ...)
```

### 4.3 Verify Web Application
Navigate to your frontend URL (e.g. `https://aether-frontend.onrender.com`):
1. Register your initial administrator/developer account.
2. Create your first project (e.g. "Primary Workspace").
3. Test uploading an artifact or creating a decision record.

---

## 5. Connecting AI Clients to Remote Render MCP Server

Aether provides public HTTPS dual-transport MCP endpoints:
- **Streamable HTTP**: `https://aether-backend.onrender.com/api/mcp`
- **Server-Sent Events (SSE)**: `https://aether-backend.onrender.com/api/mcp/sse`

First, generate an MCP access token in the Aether UI under **Project Settings > MCP Integrations > Create MCP Key**.

### 5.1 Google Antigravity
Add to your project's `.agents/mcp_config.json` or global `~/.gemini/config/mcp_config.json`:
```json
{
  "mcpServers": {
    "aether": {
      "url": "https://aether-backend.onrender.com/api/mcp",
      "headers": {
        "Authorization": "Bearer mcp_live_YOUR_TOKEN_HERE"
      }
    }
  }
}
```

### 5.2 Anthropic Claude Desktop
Because Claude Desktop requires local stdio or an HTTP bridge, configure `claude_desktop_config.json` using `mcp-remote`:
```json
{
  "mcpServers": {
    "aether": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "https://aether-backend.onrender.com/api/mcp",
        "--header",
        "Authorization:Bearer mcp_live_YOUR_TOKEN_HERE"
      ]
    }
  }
}
```

### 5.3 OpenAI ChatGPT (Custom Connector)
1. Open **ChatGPT Settings** > **Connected Apps** > **Add Custom Connector**.
2. **Server URL**: `https://aether-backend.onrender.com/api/mcp`
3. **Authentication**: Bearer Token (`mcp_live_YOUR_TOKEN_HERE`).
4. ChatGPT validates the tools schema and connects instantly via Streamable HTTP.

### 5.4 Cursor & Windsurf
Add to your IDE's MCP Configuration:
- **Transport**: `HTTP` / `Streamable HTTP`
- **URL**: `https://aether-backend.onrender.com/api/mcp`
- **Headers**:
  - `Authorization`: `Bearer mcp_live_YOUR_TOKEN_HERE`

---

## 6. External Webhooks & Local Agent Pairing

### 6.1 GitHub Webhook Configuration
1. Go to your GitHub repository > **Settings** > **Webhooks** > **Add webhook**.
2. **Payload URL**: `https://aether-backend.onrender.com/api/webhooks/github`
3. **Content type**: `application/json`
4. **Secret**: Enter the webhook secret configured in Aether Project Settings.
5. **Events**: Select `Pushes`, `Pull requests`, and `Workflow runs`.

### 6.2 Pairing the Local Go Agent
On your local machine, run:
```bash
cd agent
go run cmd/main.go --server https://aether-backend.onrender.com --pair
```
Enter the 6-character alphanumeric pairing code generated in the Aether Project Settings. The local agent will establish mutual authentication and begin syncing file trees and git states.

---

## 7. Troubleshooting & Operations

### Health Check Fails on Startup
- Ensure the backend service has at least **1.5 GB to 2 GB RAM** allocated (ONNX runtime loads the embedding model into memory on boot).
- Inspect logs to confirm database connection normalization succeeded.

### CORS Errors in Browser
- Check that the `AETHER_CORS_ALLOWED_ORIGINS` environment variable includes your frontend domain. By default, it allows `https://*.onrender.com`.

### Preserving Uploaded Artifacts
- The persistent disk mounted at `/app/data/artifacts` ensures all raw uploaded documents, logs, and artifacts survive backend restarts and redeployments.
