# ==============================================================================
# Aether Docker PostgreSQL Export Script (PowerShell for Windows)
# Safely dumps Docker PostgreSQL database for migration to Neon.
# Rule: Read-only dump; does NOT modify or delete the local Docker database.
# ==============================================================================

param (
    [string]$BackupDir = "./data/backups"
)

$ErrorActionPreference = "Stop"

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$DumpFile = Join-Path $BackupDir "aether_docker_$Timestamp.dump"
$LatestFile = Join-Path $BackupDir "aether_docker_latest.dump"

if (!(Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " Starting Safe Docker PostgreSQL Export" -ForegroundColor Cyan
Write-Host " Timestamp: $Timestamp"
Write-Host " Target File: $DumpFile"
Write-Host "==========================================================" -ForegroundColor Cyan

# Check if aether-postgres container is running
$containerRunning = docker ps --filter "name=aether-postgres" --format "{{.Names}}" 2>$null

if ($containerRunning -contains "aether-postgres") {
    Write-Host ">> Found running 'aether-postgres' Docker container." -ForegroundColor Green
    Write-Host ">> Executing pg_dump inside container..."
    docker exec aether-postgres pg_dump -U aether -d aether --format=custom --no-owner --no-acl > $DumpFile
} elseif (Get-Command pg_dump -ErrorAction SilentlyContinue) {
    Write-Host ">> Exporting via host pg_dump from localhost:5432..." -ForegroundColor Green
    $env:PGPASSWORD = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "aether_dev_password" }
    $hostName = if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "localhost" }
    $port = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { "5432" }
    $user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "aether" }
    $db = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "aether" }
    
    pg_dump -h $hostName -p $port -U $user -d $db --format=custom --no-owner --no-acl --file=$DumpFile
} else {
    Write-Error "Neither running 'aether-postgres' container nor host pg_dump was found."
    exit 1
}

Copy-Item -Path $DumpFile -Destination $LatestFile -Force
$fileInfo = Get-Item $DumpFile

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host " SUCCESS: Docker PostgreSQL dumped successfully." -ForegroundColor Green
Write-Host " File: $DumpFile ($([math]::Round($fileInfo.Length / 1MB, 2)) MB)"
Write-Host " Latest Pointer: $LatestFile"
Write-Host " Source Docker database untouched." -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Cyan
