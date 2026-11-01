# Docker Integration Specification & Capability Matrix

**Specification Baseline**: File-Based Structural Analysis & Local Agent Execution Wrapper  
**Auth Model**: Local Machine Privileges (Delegated to Local Project Agent)  
**Document Status**: **VERIFIED** as of October 2026.

---

## 1. Executive Summary & Honest Scope

Aether provides deep visibility into Dockerized container architectures through **static file analysis** (Dockerfile, docker-compose) and **local execution verification** (via `aether-agent run`), while intentionally avoiding direct Docker daemon socket connections.

### Why Docker Daemon Control is Prohibited
Direct Docker daemon access (`/var/run/docker.sock` or `tcp://localhost:2375`):
1. **Security Vulnerability**: Exposing Docker sockets grants root-equivalent host privileges and risks container breakout.
2. **Platform Inconsistency**: Socket protocols and paths differ fundamentally across Linux, macOS (Docker Desktop VM), and Windows (WSL2 / named pipes).
3. **Product Principle**: Aether is persistent memory and context exchange, NOT a container runtime or orchestrator (Constitution Rule 13: *"No unnecessary features, technology or complexity"*).

---

## 2. In-Product Capability Matrix

| Capability | Supported Method | In-Product Status | Description |
| :--- | :---: | :---: | :--- |
| **Dockerfile Parsing** | `MANUAL` / `LOCAL_AGENT` | **Supported** | Parses multi-stage builds, base images, build args, exposed ports, and command entries using Phase 3 `DockerfileChunker`. |
| **Compose Parsing** | `MANUAL` / `LOCAL_AGENT` | **Supported** | Parses `docker-compose.yml` service topologies, port mappings, networks, volume mounts, and health checks using `YamlChunker`. |
| **Build Verification** | `LOCAL_AGENT` (`run` wrapper) | **Supported** | The local agent wraps `docker build .` and records exit codes, layer outputs, build durations, and errors as immutable evidence artifacts. |
| **Test Execution** | `LOCAL_AGENT` (`run` wrapper) | **Supported** | Wraps `docker compose up --abort-on-container-exit` to record test outcomes. |
| **Direct Daemon Control** | `UNSUPPORTED` | **Not Available** | Direct daemon socket manipulation is omitted for security and portability reasons. |

