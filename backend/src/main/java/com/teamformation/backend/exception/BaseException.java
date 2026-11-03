package com.teamformation.backend.exception;

import org.springframework.http.HttpStatus;

/**
 * Base abstract runtime exception for all custom domain and application exceptions.
 */
public abstract class BaseException extends RuntimeException {

    private final HttpStatus httpStatus;

    protected BaseException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected BaseException(String message, Throwable cause, HttpStatus httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

