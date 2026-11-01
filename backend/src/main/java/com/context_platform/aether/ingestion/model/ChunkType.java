package com.context_platform.aether.ingestion.model;

public enum ChunkType {
    MARKDOWN_SECTION,
    JAVA_CLASS,
    JAVA_METHOD,
    TYPESCRIPT_DECL,
    SQL_STATEMENT,
    OPENAPI_OPERATION,
    DOCKER_STAGE,
    LOG_ENTRY,
    PLAIN_PARAGRAPH,
    JSON_BLOCK,
    YAML_SECTION
}

