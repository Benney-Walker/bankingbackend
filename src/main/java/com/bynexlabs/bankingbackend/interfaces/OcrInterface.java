package com.bynexlabs.bankingbackend.interfaces;

import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedIdDocument;

/**
 * Reads the fields of an identity document image (roadmap 3.4). Keeping the KYC flow behind this
 * interface means the OCR provider can be changed (the owner is evaluating cheaper options than
 * AWS Textract) without touching onboarding or the KYC decision logic.
 *
 * OCR only extracts text. It does not prove a document is genuine or that the person presenting
 * it owns it.
 */
public interface OcrInterface {

    /**
     * @throws RuntimeException if the document can't be analysed at all (provider error, not an
     *         identity document); the caller routes the KYC record to manual review.
     */
    ExtractedIdDocument extractIdDocument(byte[] document);
}
