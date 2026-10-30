package com.context_platform.aether.common;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends ApiException {
    public InvalidStateTransitionException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
