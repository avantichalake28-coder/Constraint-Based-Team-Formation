package com.teamformation.backend.exception;

import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * Raised when a roster add request contains SRNs that do not resolve to an
 * existing, verified student. The request is rejected as a whole and the
 * offending SRNs are reported in the standard error envelope.
 */
public class InvalidRosterSrnsException extends BaseException {

    private final List<String> invalidSrns;

    public InvalidRosterSrnsException(List<String> invalidSrns) {
        super("One or more SRNs do not belong to an existing, verified student", HttpStatus.BAD_REQUEST);
        this.invalidSrns = List.copyOf(invalidSrns);
    }

    public List<String> getInvalidSrns() {
        return invalidSrns;
    }
}
