@echo off
echo === Unified Engineering Context Platform (Aether) Setup ===

java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java 17 is required but not installed.
    exit /b 1
)

mvn -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: Maven is required but not installed.
    exit /b 1
)

npm -v >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: npm is required but not installed.
    exit /b 1
)

echo Starting PostgreSQL 16 with pgvector...
docker compose up -d

echo Installing frontend dependencies...
cd frontend
call npm install
cd ..

echo Compiling backend...
cd backend
call mvn clean compile -DskipTests
cd ..

echo === Setup complete! ===
echo Run backend: cd backend ^&^& mvn spring-boot:run
echo Run frontend: cd frontend ^&^& npm run dev
