package com.context_platform.aether.orchestrator.service;

import com.context_platform.aether.orchestrator.model.ContextRequestType;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RequestClassifier {

    public ContextRequestType classify(String taskDescription) {
        if (taskDescription == null || taskDescription.isBlank()) {
            return ContextRequestType.GENERAL;
        }

        String lower = taskDescription.toLowerCase(Locale.ROOT);

        if (lower.contains("architecture") || lower.contains("blueprint") || lower.contains("system design")
            || lower.contains("topology") || lower.contains("high-level") || lower.contains("review architecture")) {
            return ContextRequestType.ARCHITECTURE_REVIEW;
        }

        if (lower.contains("debug") || lower.contains("error") || lower.contains("exception")
            || lower.contains("crash") || lower.contains("stacktrace") || lower.contains("stack trace")
            || lower.contains("500") || lower.contains("bug") || lower.contains("failed") || lower.contains("investigate")) {
            return ContextRequestType.DEBUGGING;
        }

        if (lower.contains("test") || lower.contains("verify") || lower.contains("coverage")
            || lower.contains("benchmark") || lower.contains("assertion")) {
            return ContextRequestType.TESTING;
        }

        if (lower.contains("deploy") || lower.contains("docker") || lower.contains("render")
            || lower.contains("kubernetes") || lower.contains("ci/cd") || lower.contains("pipeline")
            || lower.contains("container")) {
            return ContextRequestType.DEPLOYMENT;
        }

        if (lower.contains("implement") || lower.contains("build") || lower.contains("add feature")
            || lower.contains("create") || lower.contains("write") || lower.contains("code")
            || lower.contains("endpoint")) {
            return ContextRequestType.IMPLEMENTATION;
        }

        return ContextRequestType.GENERAL;
    }
}

