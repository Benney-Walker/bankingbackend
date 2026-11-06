package com.bynexlabs.bankingbackend.Utility;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * An error a service raises on purpose, such as a duplicate record or a missing member.
 * GlobalExceptionHandler turns it into the standard error body with this status, and the
 * message is sent to the client as-is, so it must never contain internal details.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
