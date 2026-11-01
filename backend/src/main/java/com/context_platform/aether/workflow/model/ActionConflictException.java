package com.context_platform.aether.workflow.model;

import com.context_platform.aether.common.ApiException;
import org.springframework.http.HttpStatus;

public class ActionConflictException extends ApiException {
    public ActionConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}

