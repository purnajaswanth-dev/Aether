# Postman Integration Specification & Capability Matrix

**Specification Baseline**: Postman Public API v1  
**Auth Model**: Postman API Key (`PMAK-...`) via header `X-Api-Key`  
**API Baseline URL**: `https://api.postman.com`  
**Document Status**: **VERIFIED** against official Postman documentation as of October 2026.

---

## 1. Executive Summary & Architecture

Aether connects to Postman to index API contract collections, schemas (OpenAPI 3.0 / Swagger), and automated monitor test results as authoritative engineering artifacts, while enforcing strict credential redaction.

---

## 2. In-Product Capability Matrix

| Capability | Supported Method | Rate Limit / SLA | In-Product Status | Description |
| :--- | :---: | :---: | :---: | :--- |
| **Collection Sync** | `POLLING` / `MANUAL` | 300 req/min (Paid) / 60 (Free) | **Supported** | Imports API collections, request definitions, query params, headers, and pre-request scripts. |
| **API Schema Sync** | `POLLING` / `MANUAL` | 300 req/min (Paid) / 60 (Free) | **Supported** | Imports OpenAPI 3.0 / Swagger schemas directly into Aether's contract index. |
| **Monitor Run Results** | `POLLING` | 300 req/min (Paid) / 60 (Free) | **Supported** | Retrieves test verification results, assertion passes/failures, and execution latencies. |
| **Environment Secrets** | `UNSUPPORTED` | N/A | **Prohibited** | Environment variables containing API keys or credentials are never imported or stored. |
| **Realtime Webhooks** | `UNSUPPORTED` | N/A | **Not Available** | Postman Public API does not provide generic webhook events on collection edits. |

---

## 3. Constitutional Security Boundaries

> [!CAUTION]
> **Enforcement of Non-Negotiable Rule 8**:
> Postman environments frequently contain active production or staging secrets (`api_key`, `jwt_token`, `db_password`). Aether's Postman adapter **explicitly refuses** to query or store `GET /environments` payloads. Any collection variables matching sensitive patterns are redacted via `SecretDetectorService` before ingestion.

