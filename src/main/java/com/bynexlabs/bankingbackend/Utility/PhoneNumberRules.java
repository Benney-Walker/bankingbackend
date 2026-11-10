package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * Phone numbers are stored in the local format: exactly 10 digits starting with 0, for example
 * 0241234567 (owner rule, applied to staff and members). VynfySmsService converts them to +233
 * when sending.
 */
public final class PhoneNumberRules {

    private static final Pattern LOCAL_FORMAT = Pattern.compile("0\\d{9}");

    private PhoneNumberRules() {
    }

    public static void requireLocalFormat(String phoneNumber) {
        if (phoneNumber == null || !LOCAL_FORMAT.matcher(phoneNumber).matches()) {
            throw new BadRequestException("Invalid phone number");
        }
    }
}
