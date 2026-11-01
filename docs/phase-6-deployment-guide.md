# Phase 6 Production Deployment Guide & Runbook

**Version:** 1.0.0  
**Target Environment:** Linux / Docker 24+ / Docker Compose v2+  
**Architecture:** PostgreSQL 16 + pgvector, Spring Boot 3.3 (Java 17 LTS), Nginx + React SPA

---

## 1. System Requirements & Prerequisites

* **CPU**: 2+ Cores (4 Cores recommended for high-volume embedding inference)
* **RAM**: 4 GB minimum (8 GB recommended: 2GB Postgres, 2GB Backend JVM, 512MB Frontend/OS)
* **Storage**: 50 GB+ SSD for database and raw engineering artifacts
* **Dependencies**:
  * Docker Engine 24.0+
  * Docker Compose v2.20+
  * Ports 80 (HTTP) and 443 (HTTPS) open

---

## 2. Configuration & Secrets Setup

1. Clone the repository on the target production server:
   ```bash
   git clone <repo_url> /opt/aether
   cd /opt/aether
   ```
2. Create production `.env` from template:
   ```bash
   cp .env.example .env
   ```
3. Generate cryptographically strong random secrets:
   ```bash
   # Generate DB password
   DB_PASS=$(openssl rand -base64 32 | tr -dc 'a-zA-Z0-9' | head -c 32)
   sed -i "s/replace_with_strong_random_db_password_minimum_32_chars/${DB_PASS}/" .env

   # Generate 256-bit JWT secret
   JWT_KEY=$(openssl rand -hex 32)
   sed -i "s/replace_with_hex_encoded_256_bit_jwt_secret_key_minimum_64_chars/${JWT_KEY}/" .env

   # Generate 256-bit Master Encryption Key
   MASTER_KEY=$(openssl rand -hex 32)
   sed -i "s/replace_with_hex_encoded_256_bit_master_encryption_key/${MASTER_KEY}/" .env
   ```
4. Secure the file permissions:
   ```bash
   chmod 600 .env
   ```

---

## 3. Production Deployment Execution

### Step 1: Launch Services with Docker Compose
```bash
docker compose -f docker-compose.prod.yml up -d --build
```

### Step 2: Verify Service Health
Check that all three containers transition to `healthy`:
```bash
docker compose -f docker-compose.prod.yml ps
```
Expected output:
```text
NAME                   IMAGE                 STATUS                   PORTS
aether-prod-postgres   pgvector/pgvector:pg16 Up (healthy)             5432/tcp
aether-prod-backend    aether-backend:latest  Up (healthy)             8080/tcp
aether-prod-frontend   aether-frontend:latest Up (healthy)             0.0.0.0:80->80/tcp
```

### Step 3: Verify Actuator Health Endpoint
```bash
curl -s http://localhost/actuator/health | jq
```
Expected output:
```json
{
  "status": "UP",
  "components": {
    "aetherHealth": {
      "status": "UP",
      "details": {
        "database": "UP (PostgreSQL 16 + pgvector connected)",
        "storage": "UP (Directory writable at /app/data/artifacts)",
        "embeddingModel": "UP (ONNX model dimension=384)"
      }
    }
  }
}
```

---

## 4. Production TLS Termination Setup

In production, terminate TLS at a host-level reverse proxy (e.g., Traefik, Caddy, or Nginx with Let's Encrypt Certbot) or behind Cloudflare:

```nginx
# Host Nginx TLS Config (/etc/nginx/sites-available/aether)
server {
    listen 443 ssl http2;
    server_name aether.yourdomain.com;

    ssl_certificate /etc/letsencrypt/live/aether.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/aether.yourdomain.com/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;

    # HSTS
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;

    location / {
        proxy_pass http://127.0.0.1:80;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
```

---

## 5. Maintenance, Updates & Rollback Runbook

### Rolling Update
```bash
git pull origin main
docker compose -f docker-compose.prod.yml build
docker compose -f docker-compose.prod.yml up -d --no-deps backend frontend
```
* Flyway migrations run automatically on backend startup.
* Immutability and RLS triggers are preserved across all updates.

### Immediate Rollback Procedure
```bash
# 1. Check out previous known good Git tag
git checkout v1.0.0

# 2. Rebuild and restart containers
docker compose -f docker-compose.prod.yml up -d --build
```

### Backup Automation
Add to root crontab (`crontab -e`):
```cron
0 2 * * * docker exec aether-prod-postgres pg_dump -Fc -U aether aether > /backups/aether_db_$(date +\%F).dump
```

