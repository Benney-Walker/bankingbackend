package com.bynexlabs.bankingbackend.exception;

import org.springframework.http.HttpStatus;

/** The request clashes with data that already exists. Responds with CONFLICT (409). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
