package com.bynexlabs.bankingbackend.Utility;

import org.springframework.http.HttpStatus;

public class InternalServerError extends ApiException{
    public InternalServerError(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
