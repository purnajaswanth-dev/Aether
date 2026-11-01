#!/usr/bin/env bash
# ==============================================================================
# Aether Docker PostgreSQL Export Script
# Safely dumps Docker PostgreSQL database for migration to Neon.
# Rule: Read-only dump; does NOT modify or delete the local Docker database.
# ==============================================================================

set -euo pipefail

BACKUP_DIR="${1:-./data/backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
DUMP_FILE="${BACKUP_DIR}/aether_docker_${TIMESTAMP}.dump"
LATEST_LINK="${BACKUP_DIR}/aether_docker_latest.dump"

mkdir -p "${BACKUP_DIR}"

echo "=========================================================="
echo " Starting Safe Docker PostgreSQL Export"
echo " Timestamp: ${TIMESTAMP}"
echo " Target File: ${DUMP_FILE}"
echo "=========================================================="

# Check if Docker container is running
if docker ps --format '{{.Names}}' | grep -q "^aether-postgres$"; then
    echo ">> Found running 'aether-postgres' Docker container."
    echo ">> Executing pg_dump inside container..."
    docker exec aether-postgres pg_dump \
        -U aether \
        -d aether \
        --format=custom \
        --no-owner \
        --no-acl \
        --verbose > "${DUMP_FILE}"
elif command -v pg_dump &> /dev/null; then
    echo ">> Exporting via host pg_dump from localhost:5432..."
    PGPASSWORD="${POSTGRES_PASSWORD:-aether_dev_password}" pg_dump \
        -h "${POSTGRES_HOST:-localhost}" \
        -p "${POSTGRES_PORT:-5432}" \
        -U "${POSTGRES_USER:-aether}" \
        -d "${POSTGRES_DB:-aether}" \
        --format=custom \
        --no-owner \
        --no-acl \
        --file="${DUMP_FILE}"
else
    echo "ERROR: Neither running 'aether-postgres' container nor host pg_dump was found."
    exit 1
fi

# Create convenient pointer to latest backup
cp -f "${DUMP_FILE}" "${LATEST_LINK}"

FILE_SIZE=$(du -h "${DUMP_FILE}" | cut -f1)
echo "=========================================================="
echo " SUCCESS: Docker PostgreSQL dumped successfully."
echo " File: ${DUMP_FILE} (${FILE_SIZE})"
echo " Symlink/Copy: ${LATEST_LINK}"
echo " Source Docker database untouched."
echo "=========================================================="

