package com.bynexlabs.bankingbackend.DTO.ocr;

/**
 * One value read from an identity document.
 * - text: as printed on the document.
 * - normalizedText: the provider's normalised form, if it has one. For dates this must be ISO
 *   (yyyy-MM-dd, optionally followed by a time); the KYC check compares dates only through it,
 *   because printed dates like 05/06/1990 are ambiguous.
 * - confidence: a percentage from 0 to 100, the scale KycService's threshold is written for; a
 *   provider with a different scale must convert to this one.
 */
public record ExtractedValue(String text, String normalizedText, float confidence) {

    public ExtractedValue(String text, float confidence) {
        this(text, null, confidence);
    }
}
