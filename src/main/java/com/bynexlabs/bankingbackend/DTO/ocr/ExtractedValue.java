package com.bynexlabs.bankingbackend.DTO.ocr;

/**
 * One value read from an identity document. confidence is a percentage from 0 to 100, which is
 * what KycService's thresholds are written against; a provider with a different scale must
 * convert to this one.
 */
public record ExtractedValue(String text, float confidence) {
}
