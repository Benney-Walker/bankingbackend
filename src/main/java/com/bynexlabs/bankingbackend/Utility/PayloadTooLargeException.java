package com.bynexlabs.bankingbackend.Utility;

import org.springframework.http.HttpStatus;

/** An uploaded file is bigger than allowed. Responds with 413. */
public class PayloadTooLargeException extends ApiException {

    // Looked up by code: Spring renamed PAYLOAD_TOO_LARGE to CONTENT_TOO_LARGE (RFC 9110), and
    // the code works whichever constant this Spring version has.
    static final HttpStatus STATUS = HttpStatus.valueOf(413);

    public PayloadTooLargeException(String message) {
        super(STATUS, message);
    }
}
