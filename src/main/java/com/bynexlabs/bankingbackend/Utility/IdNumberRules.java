package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;

import java.util.regex.Pattern;

/**
 * Format rules for ID document numbers, per card type (owner decision D10, 2026-11-07). Numbers
 * arrive already trimmed and upper-cased.
 *
 * - ECOWAS_CARD is the Ghana Card: its PIN is "GHA-", 9 digits, "-", 1 check digit, for example
 *   GHA-123456789-0. Only the shape is checked; the check-digit algorithm isn't public.
 * - VOTER_CARD: no format rule yet; the owner will confirm the format.
 */
public final class IdNumberRules {

    private static final Pattern GHANA_CARD_PIN = Pattern.compile("GHA-\\d{9}-\\d");

    private IdNumberRules() {
    }

    public static void requireValidFormat(KycDocumentType documentType, String idNumber) {
        if (documentType == KycDocumentType.ECOWAS_CARD && !GHANA_CARD_PIN.matcher(idNumber).matches()) {
            throw new BadRequestException("Ghana Card number must look like GHA-123456789-0");
        }
    }
}
