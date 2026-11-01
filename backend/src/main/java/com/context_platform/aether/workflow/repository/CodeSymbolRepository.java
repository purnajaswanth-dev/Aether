package com.context_platform.aether.workflow.repository;

import com.context_platform.aether.workflow.model.CodeSymbolRecord;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class CodeSymbolRepository {

    private final JdbcClient jdbcClient;
    private final RowMapper<CodeSymbolRecord> rowMapper;

    public CodeSymbolRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
        this.rowMapper = (rs, rowNum) -> new CodeSymbolRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getString("file_path"),
                rs.getString("language"),
                rs.getString("symbol_name"),
                rs.getString("symbol_type"),
                rs.getString("signature"),
                rs.getInt("start_line"),
                rs.getInt("end_line"),
                rs.getTimestamp("created_at") != null ? rs.getTimestamp("created_at").toInstant() : Instant.now()
        );
    }

    public void save(CodeSymbolRecord symbol) {
        UUID id = symbol.id() != null ? symbol.id() : UUID.randomUUID();

        jdbcClient.sql("""
                INSERT INTO code_symbols (id, project_id, file_path, language, symbol_name, symbol_type, signature, start_line, end_line, created_at)
                VALUES (:id, :projectId, :filePath, :language, :symbolName, :symbolType, :signature, :startLine, :endLine, NOW())
                """)
                .param("id", id)
                .param("projectId", symbol.projectId())
                .param("filePath", symbol.filePath())
                .param("language", symbol.language())
                .param("symbolName", symbol.symbolName())
                .param("symbolType", symbol.symbolType())
                .param("signature", symbol.signature())
                .param("startLine", symbol.startLine())
                .param("endLine", symbol.endLine())
                .update();
    }

    public List<CodeSymbolRecord> findByFilePath(UUID projectId, String filePath) {
        return jdbcClient.sql("""
                SELECT * FROM code_symbols
                WHERE project_id = :projectId AND file_path = :filePath
                ORDER BY start_line ASC
                """)
                .param("projectId", projectId)
                .param("filePath", filePath)
                .query(rowMapper)
                .list();
    }

    public List<CodeSymbolRecord> findByName(UUID projectId, String symbolName) {
        return jdbcClient.sql("""
                SELECT * FROM code_symbols
                WHERE project_id = :projectId AND symbol_name ILIKE :symbolName
                ORDER BY file_path ASC
                """)
                .param("projectId", projectId)
                .param("symbolName", "%" + symbolName + "%")
                .query(rowMapper)
                .list();
    }

    public void deleteByFilePath(UUID projectId, String filePath) {
        jdbcClient.sql("""
                DELETE FROM code_symbols WHERE project_id = :projectId AND file_path = :filePath
                """)
                .param("projectId", projectId)
                .param("filePath", filePath)
                .update();
    }
}

