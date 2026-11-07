package com.bynexlabs.bankingbackend.Utility;

import org.springframework.http.HttpStatus;

/**
 * The caller is authenticated and has the right role, but a business rule says this particular
 * staff member may not do this, e.g. re-uploading a KYC document for a member someone else
 * registered. Responds with FORBIDDEN (403).
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, message);
    }
}
