package com.bynexlabs.bankingbackend.exception;

import org.springframework.http.HttpStatus;

/** The requested record does not exist. Responds with NOT FOUND (404). */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
