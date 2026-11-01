package com.context_platform.aether;

import com.context_platform.aether.ingestion.chunker.*;
import com.context_platform.aether.ingestion.model.ArtifactChunk;
import com.context_platform.aether.ingestion.model.ChunkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 3: Structure-Aware Chunker Tests")
public class ChunkerTestSuite {

    private final MarkdownChunker markdownChunker = new MarkdownChunker();
    private final JavaChunker javaChunker = new JavaChunker();
    private final TypeScriptChunker typeScriptChunker = new TypeScriptChunker();
    private final SqlChunker sqlChunker = new SqlChunker();
    private final OpenApiChunker openApiChunker = new OpenApiChunker();
    private final DockerChunker dockerChunker = new DockerChunker();
    private final LogChunker logChunker = new LogChunker();
    private final JsonChunker jsonChunker = new JsonChunker(new com.fasterxml.jackson.databind.ObjectMapper());
    private final YamlChunker yamlChunker = new YamlChunker();
    private final PlainTextChunker plainTextChunker = new PlainTextChunker();

    private final UUID projectId = UUID.randomUUID();
    private final UUID artifactId = UUID.randomUUID();

    @Test
    @DisplayName("MarkdownChunker preserves heading hierarchies and code blocks intact")
    void testMarkdownChunker() {
        String md = """
            # Architecture Blueprint
            
            High level system overview.
            
            ## Database Layer
            
            PostgreSQL 16 with pgvector extension.
            
            ```sql
            CREATE TABLE users (id UUID PRIMARY KEY);
            ```
            
            ### Row Level Security
            
            All tenant data is isolated via RLS.
            """;

        List<ArtifactChunk> chunks = markdownChunker.chunk(
            projectId, artifactId, 1, md, "Architecture.md", Map.of()
        );

        assertFalse(chunks.isEmpty());
        assertEquals(3, chunks.size());

        ArtifactChunk c1 = chunks.get(0);
        assertTrue(c1.parentSectionPath().contains("Architecture Blueprint"));
        assertEquals(ChunkType.MARKDOWN_SECTION, c1.chunkType());

        ArtifactChunk c2 = chunks.get(1);
        assertTrue(c2.parentSectionPath().contains("Database Layer"));
        assertTrue(c2.content().contains("```sql")); // code block kept intact

        ArtifactChunk c3 = chunks.get(2);
        assertTrue(c3.parentSectionPath().contains("Row Level Security"));
    }

    @Test
    @DisplayName("JavaChunker preserves class declarations and method blocks")
    void testJavaChunker() {
        String javaCode = """
            package com.example;
            
            public class UserService {
            
                public User getUserById(UUID id) {
                    return userRepository.findById(id);
                }
            
                public void deleteUser(UUID id) {
                    userRepository.deleteById(id);
                }
            }
            """;

        List<ArtifactChunk> chunks = javaChunker.chunk(
            projectId, artifactId, 1, javaCode, "UserService.java", Map.of()
        );

        assertEquals(2, chunks.size());
        assertEquals(ChunkType.JAVA_METHOD, chunks.get(0).chunkType());
        assertTrue(chunks.get(0).parentSectionPath().contains("getUserById"));
        assertEquals(ChunkType.JAVA_METHOD, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).parentSectionPath().contains("deleteUser"));
    }

    @Test
    @DisplayName("TypeScriptChunker extracts top-level functions and declarations")
    void testTypeScriptChunker() {
        String tsCode = """
            export interface Project {
                id: string;
                name: string;
            }
            
            export async function fetchProjects(): Promise<Project[]> {
                const res = await api.get('/projects');
                return res.data;
            }
            """;

        List<ArtifactChunk> chunks = typeScriptChunker.chunk(
            projectId, artifactId, 1, tsCode, "projects.ts", Map.of()
        );

        assertEquals(2, chunks.size());
        assertEquals(ChunkType.TYPESCRIPT_DECL, chunks.get(0).chunkType());
        assertTrue(chunks.get(0).parentSectionPath().contains("Project"));
        assertEquals(ChunkType.TYPESCRIPT_DECL, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).parentSectionPath().contains("fetchProjects"));
    }

    @Test
    @DisplayName("SqlChunker splits on individual DDL/DML statements")
    void testSqlChunker() {
        String sql = """
            -- Migration V1
            CREATE TABLE projects (
                id UUID PRIMARY KEY,
                name VARCHAR(255) NOT NULL
            );
            
            CREATE INDEX idx_projects_name ON projects(name);
            
            INSERT INTO projects (id, name) VALUES ('00000000-0000-0000-0000-000000000001', 'Default');
            """;

        List<ArtifactChunk> chunks = sqlChunker.chunk(
            projectId, artifactId, 1, sql, "V1__schema.sql", Map.of()
        );

        assertEquals(3, chunks.size());
        assertEquals(ChunkType.SQL_STATEMENT, chunks.get(0).chunkType());
        assertTrue(chunks.get(0).content().contains("CREATE TABLE projects"));
        assertEquals(ChunkType.SQL_STATEMENT, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).content().contains("CREATE INDEX idx_projects_name"));
    }

    @Test
    @DisplayName("OpenApiChunker preserves endpoint path and method blocks")
    void testOpenApiChunker() {
        String openapi = """
            paths:
              /api/v1/projects:
                get:
                  summary: List all projects
                  responses:
                    '200':
                      description: OK
                post:
                  summary: Create a project
                  responses:
                    '201':
                      description: Created
            """;

        List<ArtifactChunk> chunks = openApiChunker.chunk(
            projectId, artifactId, 1, openapi, "openapi.yaml", Map.of()
        );

        assertEquals(3, chunks.size());
        assertEquals(ChunkType.OPENAPI_OPERATION, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).parentSectionPath().contains("GET /api/v1/projects"));
        assertEquals(ChunkType.OPENAPI_OPERATION, chunks.get(2).chunkType());
        assertTrue(chunks.get(2).parentSectionPath().contains("POST /api/v1/projects"));
    }

    @Test
    @DisplayName("DockerChunker extracts build stages and compose services")
    void testDockerChunker() {
        String dockerfile = """
            FROM maven:3.9-eclipse-temurin-17 AS builder
            WORKDIR /app
            COPY . .
            RUN mvn package -DskipTests
            
            FROM eclipse-temurin:17-jre-jammy AS runtime
            WORKDIR /app
            COPY --from=builder /app/target/*.jar app.jar
            ENTRYPOINT ["java", "-jar", "app.jar"]
            """;

        List<ArtifactChunk> chunks = dockerChunker.chunk(
            projectId, artifactId, 1, dockerfile, "Dockerfile", Map.of()
        );

        assertEquals(2, chunks.size());
        assertEquals(ChunkType.DOCKER_STAGE, chunks.get(0).chunkType());
        assertTrue(chunks.get(0).parentSectionPath().contains("builder"));
        assertEquals(ChunkType.DOCKER_STAGE, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).parentSectionPath().contains("runtime"));
    }

    @Test
    @DisplayName("LogChunker groups log entries and stack traces cleanly")
    void testLogChunker() {
        String logs = """
            2026-10-31T10:00:00.100Z INFO [app] Server initialized successfully
            2026-10-31T10:00:01.200Z ERROR [app] Database connection refused
            java.sql.SQLException: Connection refused at localhost:5432
                at com.zaxxer.hikari.pool.HikariPool.throwPoolInitializationException
                at com.zaxxer.hikari.pool.HikariPool.checkFailFast
            2026-10-31T10:00:05.300Z INFO [app] Retrying database connection in 5s
            """;

        List<ArtifactChunk> chunks = logChunker.chunk(
            projectId, artifactId, 1, logs, "server.log", Map.of()
        );

        assertFalse(chunks.isEmpty());
        assertTrue(chunks.get(0).content().contains("ERROR"));
        assertTrue(chunks.get(0).content().contains("java.sql.SQLException"));
    }

    @Test
    @DisplayName("JsonChunker splits JSON objects along top-level key boundaries")
    void testJsonChunker() {
        String json = """
            {
              "database": {
                "host": "localhost",
                "port": 5432,
                "name": "aether"
              },
              "security": {
                "algorithm": "Argon2id",
                "saltLength": 16
              }
            }
            """;

        List<ArtifactChunk> chunks = jsonChunker.chunk(
            projectId, artifactId, 1, json, "config.json", Map.of()
        );

        assertEquals(2, chunks.size());
        assertEquals(ChunkType.JSON_BLOCK, chunks.get(0).chunkType());
        assertTrue(chunks.get(0).parentSectionPath().contains("database"));
        assertTrue(chunks.get(0).content().contains("5432"));
        assertEquals(ChunkType.JSON_BLOCK, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).parentSectionPath().contains("security"));
        assertTrue(chunks.get(1).content().contains("Argon2id"));
    }

    @Test
    @DisplayName("YamlChunker parses YAML document and top-level block boundaries")
    void testYamlChunker() {
        String yaml = """
            server:
              port: 8080
              context-path: /api/v1
            spring:
              datasource:
                url: jdbc:postgresql://localhost:5432/aether
                username: aether_app
            """;

        List<ArtifactChunk> chunks = yamlChunker.chunk(
            projectId, artifactId, 1, yaml, "application.yaml", Map.of()
        );

        assertEquals(2, chunks.size());
        assertEquals(ChunkType.YAML_SECTION, chunks.get(0).chunkType());
        assertTrue(chunks.get(0).parentSectionPath().contains("server"));
        assertTrue(chunks.get(0).content().contains("8080"));
        assertEquals(ChunkType.YAML_SECTION, chunks.get(1).chunkType());
        assertTrue(chunks.get(1).parentSectionPath().contains("spring"));
        assertTrue(chunks.get(1).content().contains("aether_app"));
    }
}

