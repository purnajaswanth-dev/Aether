package com.context_platform.aether.common;

import org.springframework.http.HttpStatus;

public class OptimisticLockingException extends ApiException {
    public OptimisticLockingException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}

