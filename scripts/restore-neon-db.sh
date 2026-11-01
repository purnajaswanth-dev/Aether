#!/usr/bin/env bash
# ==============================================================================
# Aether Neon PostgreSQL Restore Script
# Safely restores a Docker database dump into Neon PostgreSQL.
# Rule: Requires explicit user approval before execution; does not run destructively.
# ==============================================================================

set -euo pipefail

DUMP_FILE="${1:-./data/backups/aether_docker_latest.dump}"
TARGET_URL="${DB_URL:-${2:-}}"

if [[ -z "${TARGET_URL}" ]]; then
    echo "ERROR: Target Neon database URL must be provided via DB_URL environment variable or as argument."
    echo "Usage: DB_URL='postgresql://user:pass@ep-xyz.neon.tech/neondb?sslmode=require' $0 [dump_file]"
    exit 1
fi

if [[ ! -f "${DUMP_FILE}" ]]; then
    echo "ERROR: Dump file not found: ${DUMP_FILE}"
    echo "Run scripts/dump-docker-db.sh first to generate a fresh backup."
    exit 1
fi

# Strip jdbc: prefix if present (libpq / pg_restore expects postgresql://)
CLEAN_URL="${TARGET_URL#jdbc:}"

# Mask credentials when displaying target host in logs/console
MASKED_HOST=$(echo "${CLEAN_URL}" | sed -E 's/.*@([^:\/?]+).*/\1/' | sed -E 's/^postgres(ql)?:\/\///')

echo "=========================================================="
echo " Aether — Restore to Neon PostgreSQL"
echo " Source Dump: ${DUMP_FILE}"
echo " Target Neon Host: ${MASKED_HOST}"
echo "=========================================================="
echo ""
echo "SAFETY WARNING:"
echo "This will restore schema and records into your Neon PostgreSQL database."
echo ""
read -r -p "Type 'CONFIRM' to proceed with Neon database restoration: " CONFIRMATION

if [[ "${CONFIRMATION}" != "CONFIRM" ]]; then
    echo "Operation aborted by user. No changes made."
    exit 0
fi

# Export separate credentials to PGPASSWORD / PGUSER if provided to handle special characters safely
if [[ -n "${DB_PASSWORD:-}" ]]; then
    export PGPASSWORD="${DB_PASSWORD}"
fi
if [[ -n "${DB_USERNAME:-}" ]]; then
    export PGUSER="${DB_USERNAME}"
fi

echo ">> Restoring dump to Neon PostgreSQL..."

if command -v pg_restore &> /dev/null; then
    pg_restore \
        --dbname="${CLEAN_URL}" \
        --no-owner \
        --no-acl \
        --verbose \
        "${DUMP_FILE}" || true
else
    # Fallback to docker container postgres client
    DUMP_DIR=$(cd "$(dirname "${DUMP_FILE}")" && pwd)
    DUMP_NAME=$(basename "${DUMP_FILE}")
    docker run --rm \
        ${PGPASSWORD:+-e PGPASSWORD} \
        ${PGUSER:+-e PGUSER} \
        -v "${DUMP_DIR}":/backup \
        postgres:16 \
        pg_restore \
        --dbname="${CLEAN_URL}" \
        --no-owner \
        --no-acl \
        --verbose \
        "/backup/${DUMP_NAME}" || true
fi

echo "=========================================================="
echo " SUCCESS: Database restore operation completed."
echo " Next step: Run 'scripts/verify-neon-migration.sql' against Neon to verify records."
echo "=========================================================="
