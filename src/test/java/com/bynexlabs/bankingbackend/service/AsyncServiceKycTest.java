package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedIdDocument;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.interfaces.OcrInterface;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Automated KYC orchestration (roadmap 3.1): external calls happen between short transactions. */
@ExtendWith(MockitoExtension.class)
class AsyncServiceKycTest {

    @Mock
    private MemberService memberService;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private MemberAccountRepo memberAccountRepo;

    @Mock
    private StorageService storageService;

    @Mock
    private KycService kycService;

    @Mock
    private OcrInterface ocrService;

    @Mock
    private ProcessedMessageService processedMessageService;

    @InjectMocks
    private AsyncService asyncService;

    private final UUID recordId = UUID.randomUUID();
    private final String key = "kyc/member/doc.jpg";

    private KycVerificationEvent event() {
        return KycVerificationEvent.builder().kycRecordId(recordId).build();
    }

    @Test
    void downloadsFromR2RunsOcrAndCompletes() {
        byte[] document = {1, 2, 3};
        ExtractedIdDocument extracted = new ExtractedIdDocument(Map.of());
        when(kycService.startAutomatedVerification(recordId))
                .thenReturn(Optional.of(new KycService.VerificationTask(recordId, key)));
        when(storageService.downloadKycDocument(key)).thenReturn(document);
        when(ocrService.extractIdDocument(document)).thenReturn(extracted);

        asyncService.processKycVerification(event());

        verify(kycService).completeAutomatedVerification(recordId, extracted);
        verify(kycService, never()).sendToManualReview(any(), anyString());
    }

    @Test
    void recordAlreadyHandledIsSkipped() {
        when(kycService.startAutomatedVerification(recordId)).thenReturn(Optional.empty());

        asyncService.processKycVerification(event());

        verifyNoInteractions(storageService);
        verify(kycService, never()).completeAutomatedVerification(any(), any());
    }

    @Test
    void storageOrOcrFailureGoesToManualReview() {
        when(kycService.startAutomatedVerification(recordId))
                .thenReturn(Optional.of(new KycService.VerificationTask(recordId, key)));
        when(storageService.downloadKycDocument(key)).thenThrow(SdkClientException.create("R2 unreachable"));

        asyncService.processKycVerification(event());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(kycService).sendToManualReview(eq(recordId), reason.capture());
        assertThat(reason.getValue()).contains("SdkClientException");
        verify(kycService, never()).completeAutomatedVerification(any(), any());
    }
}
