package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedIdDocument;
import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedValue;
import com.bynexlabs.bankingbackend.DTO.ocr.IdDocumentField;
import com.bynexlabs.bankingbackend.interfaces.OcrInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeIdRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeIdResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.IdentityDocument;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

import java.util.EnumMap;
import java.util.Map;

/**
 * OcrInterface backed by AWS Textract AnalyzeID. Textract is the current provider, not a final
 * choice; everything Textract-specific stays in this class.
 */
@Service
@RequiredArgsConstructor
public class TextractOcrService implements OcrInterface {

    // Textract's AnalyzeID field names for the fields the KYC check uses.
    private static final Map<String, IdDocumentField> TEXTRACT_FIELDS = Map.of(
            "FIRST_NAME", IdDocumentField.FIRST_NAME,
            "MIDDLE_NAME", IdDocumentField.MIDDLE_NAME,
            "LAST_NAME", IdDocumentField.LAST_NAME,
            "DATE_OF_BIRTH", IdDocumentField.DATE_OF_BIRTH,
            "DOCUMENT_NUMBER", IdDocumentField.DOCUMENT_NUMBER
    );

    private final TextractClient textractClient;

    @Override
    public ExtractedIdDocument extractIdDocument(byte[] document) {
        AnalyzeIdRequest request = AnalyzeIdRequest.builder()
                .documentPages(Document.builder().bytes(SdkBytes.fromByteArray(document)).build())
                .build();
        AnalyzeIdResponse response = textractClient.analyzeID(request);

        if (response.identityDocuments().isEmpty()) {
            throw new IllegalStateException("Textract found no identity document in the image");
        }
        IdentityDocument identityDocument = response.identityDocuments().get(0);

        Map<IdDocumentField, ExtractedValue> fields = new EnumMap<>(IdDocumentField.class);
        for (IdentityDocumentField field : identityDocument.identityDocumentFields()) {
            if (field.type() == null || field.valueDetection() == null) {
                continue;
            }
            IdDocumentField mapped = TEXTRACT_FIELDS.get(field.type().text());
            if (mapped != null) {
                Float confidence = field.valueDetection().confidence();
                // Textract reports confidence as 0-100, the scale ExtractedValue expects.
                fields.putIfAbsent(mapped, new ExtractedValue(
                        field.valueDetection().text(), confidence == null ? 0f : confidence));
            }
        }
        return new ExtractedIdDocument(fields);
    }
}
