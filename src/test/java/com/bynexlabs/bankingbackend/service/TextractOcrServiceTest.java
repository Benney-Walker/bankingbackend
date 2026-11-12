package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedIdDocument;
import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedValue;
import com.bynexlabs.bankingbackend.DTO.ocr.IdDocumentField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeIDDetections;
import software.amazon.awssdk.services.textract.model.AnalyzeIdRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeIdResponse;
import software.amazon.awssdk.services.textract.model.IdentityDocument;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;
import software.amazon.awssdk.services.textract.model.NormalizedValue;
import software.amazon.awssdk.services.textract.model.ValueType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** The Textract adapter maps AnalyzeID output onto the provider-neutral OCR types (roadmap 3.4). */
@ExtendWith(MockitoExtension.class)
class TextractOcrServiceTest {

    @Mock
    private TextractClient textractClient;

    @InjectMocks
    private TextractOcrService ocrService;

    private static IdentityDocumentField field(String type, String value, Float confidence) {
        return IdentityDocumentField.builder()
                .type(AnalyzeIDDetections.builder().text(type).build())
                .valueDetection(AnalyzeIDDetections.builder().text(value).confidence(confidence).build())
                .build();
    }

    private void givenTextractReturns(IdentityDocumentField... fields) {
        when(textractClient.analyzeID(any(AnalyzeIdRequest.class))).thenReturn(AnalyzeIdResponse.builder()
                .identityDocuments(IdentityDocument.builder().identityDocumentFields(fields).build())
                .build());
    }

    @Test
    void mapsTheFieldsTheKycCheckUses() {
        givenTextractReturns(
                field("FIRST_NAME", "AMA", 98.5f),
                field("LAST_NAME", "MENSAH", 97f),
                field("DATE_OF_BIRTH", "14/05/1990", 95f),
                field("DOCUMENT_NUMBER", "GHA-123456789-0", 91f),
                field("ADDRESS", "Tarkwa", 80f));

        ExtractedIdDocument result = ocrService.extractIdDocument(new byte[]{1});

        assertThat(result.get(IdDocumentField.FIRST_NAME)).contains(new ExtractedValue("AMA", 98.5f));
        assertThat(result.get(IdDocumentField.LAST_NAME)).contains(new ExtractedValue("MENSAH", 97f));
        assertThat(result.get(IdDocumentField.DATE_OF_BIRTH)).contains(new ExtractedValue("14/05/1990", 95f));
        assertThat(result.get(IdDocumentField.DOCUMENT_NUMBER)).contains(new ExtractedValue("GHA-123456789-0", 91f));
        assertThat(result.get(IdDocumentField.MIDDLE_NAME)).isEmpty();
        assertThat(result.fields()).hasSize(4);
    }

    @Test
    void keepsTextractsNormalisedDate() {
        givenTextractReturns(IdentityDocumentField.builder()
                .type(AnalyzeIDDetections.builder().text("DATE_OF_BIRTH").build())
                .valueDetection(AnalyzeIDDetections.builder()
                        .text("14/05/1990")
                        .normalizedValue(NormalizedValue.builder().value("1990-05-14T00:00:00").valueType(ValueType.DATE).build())
                        .confidence(96f)
                        .build())
                .build());

        assertThat(ocrService.extractIdDocument(new byte[]{1}).get(IdDocumentField.DATE_OF_BIRTH))
                .contains(new ExtractedValue("14/05/1990", "1990-05-14T00:00:00", 96f));
    }

    @Test
    void missingConfidenceCountsAsZero() {
        givenTextractReturns(field("FIRST_NAME", "AMA", null));

        assertThat(ocrService.extractIdDocument(new byte[]{1}).get(IdDocumentField.FIRST_NAME))
                .contains(new ExtractedValue("AMA", 0f));
    }

    @Test
    void imageWithoutIdentityDocumentFails() {
        when(textractClient.analyzeID(any(AnalyzeIdRequest.class))).thenReturn(AnalyzeIdResponse.builder().build());

        assertThatThrownBy(() -> ocrService.extractIdDocument(new byte[]{1}))
                .isInstanceOf(IllegalStateException.class);
    }
}
