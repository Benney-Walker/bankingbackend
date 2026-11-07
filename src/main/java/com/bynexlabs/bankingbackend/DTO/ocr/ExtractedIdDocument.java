package com.bynexlabs.bankingbackend.DTO.ocr;

import java.util.Map;
import java.util.Optional;

/** What an OCR provider could read from an identity document. Missing fields are simply absent. */
public record ExtractedIdDocument(Map<IdDocumentField, ExtractedValue> fields) {

    public ExtractedIdDocument {
        fields = Map.copyOf(fields);
    }

    public Optional<ExtractedValue> get(IdDocumentField field) {
        return Optional.ofNullable(fields.get(field));
    }
}
