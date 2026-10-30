#!/usr/bin/env bash
set -e

echo "=== Unified Engineering Context Platform (Aether) Setup ==="

# Check Java 17
if ! command -v java &> /dev/null; then
    echo "Error: Java 17 is required but not installed."
    exit 1
fi
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
echo "Java detected: $JAVA_VERSION"

# Check Maven
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is required but not installed."
    exit 1
fi

# Check Node & npm
if ! command -v npm &> /dev/null; then
    echo "Error: npm is required but not installed."
    exit 1
fi

# Start Docker containers
echo "Starting PostgreSQL 16 with pgvector..."
docker compose up -d

echo "Installing frontend dependencies..."
cd frontend && npm install && cd ..

echo "Building backend..."
cd backend && mvn clean compile -DskipTests && cd ..

echo "=== Setup complete! ==="
echo "Run backend: cd backend && mvn spring-boot:run"
echo "Run frontend: cd frontend && npm run dev"
