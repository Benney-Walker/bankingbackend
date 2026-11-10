package com.bynexlabs.bankingbackend.exception;

import org.springframework.http.HttpStatus;

/** The request is well-formed JSON but its content is invalid. Responds with BAD REQUEST (400). */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
