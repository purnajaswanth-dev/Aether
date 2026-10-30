package com.context_platform.aether.common;

import org.springframework.http.HttpStatus;

public class ProjectAccessDeniedException extends ApiException {
    public ProjectAccessDeniedException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
