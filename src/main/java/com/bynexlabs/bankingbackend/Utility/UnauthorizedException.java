package com.bynexlabs.bankingbackend.Utility;

import org.springframework.http.HttpStatus;

/** The caller could not be authenticated, for example with an invalid refresh token. Responds with UNAUTHORIZED (401). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
