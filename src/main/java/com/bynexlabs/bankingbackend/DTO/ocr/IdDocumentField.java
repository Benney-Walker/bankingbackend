package com.bynexlabs.bankingbackend.DTO.ocr;

/**
 * Fields the KYC check reads from an identity document, independent of the OCR provider. Each
 * provider maps its own field names onto these.
 */
public enum IdDocumentField {
    FIRST_NAME,
    MIDDLE_NAME,
    LAST_NAME,
    DATE_OF_BIRTH,
    DOCUMENT_NUMBER
}
