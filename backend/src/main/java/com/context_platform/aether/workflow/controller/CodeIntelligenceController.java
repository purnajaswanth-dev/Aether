package com.context_platform.aether.workflow.controller;

import com.context_platform.aether.workflow.model.dto.ChangeImpactResult;
import com.context_platform.aether.workflow.service.CodeIntelligenceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/code-intel")
public class CodeIntelligenceController {

    private final CodeIntelligenceService codeIntelligenceService;

    public CodeIntelligenceController(CodeIntelligenceService codeIntelligenceService) {
        this.codeIntelligenceService = codeIntelligenceService;
    }

    @PostMapping("/impact")
    public ResponseEntity<ChangeImpactResult> analyzeImpact(
            @PathVariable UUID projectId,
            @RequestBody Map<String, List<String>> body
    ) {
        List<String> files = body.getOrDefault("files", List.of());
        return ResponseEntity.ok(codeIntelligenceService.analyzeChangeImpact(projectId, files));
    }

    @PostMapping("/tests")
    public ResponseEntity<List<String>> getRelevantTests(
            @PathVariable UUID projectId,
            @RequestBody Map<String, List<String>> body
    ) {
        List<String> files = body.getOrDefault("files", List.of());
        return ResponseEntity.ok(codeIntelligenceService.getRelevantTests(projectId, files));
    }

    @PostMapping("/index")
    public ResponseEntity<Map<String, String>> indexFile(
            @PathVariable UUID projectId,
            @RequestBody Map<String, String> body
    ) {
        String filePath = body.get("filePath");
        String content = body.get("content");
        if (filePath != null && content != null) {
            codeIntelligenceService.indexSource(projectId, filePath, content);
        }
        return ResponseEntity.ok(Map.of("status", "INDEXED", "filePath", filePath != null ? filePath : ""));
    }
}

