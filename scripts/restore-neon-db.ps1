# ==============================================================================
# Aether Neon PostgreSQL Restore Script (PowerShell for Windows)
# Safely restores a Docker database dump into Neon PostgreSQL.
# Rule: Requires explicit user approval before execution; does not run destructively.
# ==============================================================================

param (
    [string]$DumpFile = "./data/backups/aether_docker_latest.dump",
    [string]$TargetUrl = $env:DB_URL
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($TargetUrl)) {
    Write-Error "Target Neon database URL must be provided via -TargetUrl parameter or `$env:DB_URL."
    exit 1
}

if (!(Test-Path $DumpFile)) {
    Write-Error "Dump file not found: $DumpFile. Run scripts/dump-docker-db.ps1 first."
    exit 1
}

$maskedHost = if ($TargetUrl -match "@([^:\/]+)") { $matches[1] } else { "configured host" }

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Aether — Restore to Neon PostgreSQL" -ForegroundColor Cyan
Write-Host " Source Dump: $DumpFile"
Write-Host " Target Neon Host: $maskedHost"
Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "SAFETY WARNING:" -ForegroundColor Yellow
Write-Host "This will restore schema and records into your Neon PostgreSQL database." -ForegroundColor Yellow
Write-Host ""

$confirmation = Read-Host "Type 'CONFIRM' to proceed with Neon database restoration"

if ($confirmation -ne "CONFIRM") {
    Write-Host "Operation aborted by user. No changes made." -ForegroundColor Red
    exit 0
}

Write-Host ">> Restoring dump to Neon PostgreSQL..." -ForegroundColor Green

if (Get-Command pg_restore -ErrorAction SilentlyContinue) {
    pg_restore --dbname=$TargetUrl --no-owner --no-acl --verbose $DumpFile
} else {
    $fullDumpPath = (Resolve-Path $DumpFile).Path
    $dumpDir = Split-Path -Parent $fullDumpPath
    $dumpName = Split-Path -Leaf $fullDumpPath

    docker run --rm -v "${dumpDir}:/backup" postgres:16 pg_restore --dbname=$TargetUrl --no-owner --no-acl --verbose "/backup/$dumpName"
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " SUCCESS: Database restore operation completed." -ForegroundColor Green
Write-Host " Next step: Run 'scripts/verify-neon-migration.sql' against Neon to verify records."
Write-Host "==========================================================" -ForegroundColor Cyan
