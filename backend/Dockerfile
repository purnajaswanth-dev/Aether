# ==============================================================================
# Aether Unified Full-Stack Production Dockerfile
# Stage 1: Build React SPA Frontend
# Stage 2: Compile & Package Spring Boot Backend with embedded static resources
# Stage 3: Minimal hardened runtime image (Eclipse Temurin 17 JRE)
# ==============================================================================

# Stage 1: Build Frontend SPA
FROM node:20-alpine AS frontend-builder
WORKDIR /workspace/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend ./
RUN npm run build

# Stage 2: Build Spring Boot executable JAR
FROM maven:3.9.6-eclipse-temurin-17-jammy AS backend-builder
WORKDIR /workspace

# Cache dependencies
COPY backend/pom.xml ./pom.xml
RUN mvn dependency:go-offline -B

# Compile and package application
COPY backend/src ./src

# Embed compiled React frontend assets into Spring Boot static resources directory
COPY --from=frontend-builder /workspace/frontend/dist ./src/main/resources/static

RUN mvn clean package -DskipTests -B

# Stage 3: Minimal runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Install curl for container health check
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root system user and group (UID 10001)
RUN groupadd -g 10001 aether && \
    useradd -u 10001 -g aether -s /bin/bash -m aether

# Prepare application directories
RUN mkdir -p /app/data/artifacts /app/logs && \
    chown -R aether:aether /app

# Copy application JAR from builder stage
COPY --from=backend-builder --chown=aether:aether /workspace/target/aether-backend-*.jar /app/app.jar

# Switch to non-root user
USER aether:aether

# Configure environment variables
ENV SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080 \
    AETHER_STORAGE_LOCAL_PATH=/app/data/artifacts

# Expose standard application port
EXPOSE 8080

# Health check against Spring Boot Actuator
HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=3 \
    CMD sh -c 'curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1'

# Production JVM flags tuned for containers (cgroups memory and CPU awareness)
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+UseG1GC", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "/app/app.jar"]
