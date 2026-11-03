package com.teamformation.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a business rule or validation condition is violated.
 */
public class BusinessException extends BaseException {

    public BusinessException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    public BusinessException(String message, HttpStatus status) {
        super(message, status);
    }
}

