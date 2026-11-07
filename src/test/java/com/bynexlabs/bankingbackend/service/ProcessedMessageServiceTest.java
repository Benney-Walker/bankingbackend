package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.ProcessedMessageRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessedMessageServiceTest {

    @Mock
    private ProcessedMessageRepo processedMessageRepo;

    @InjectMocks
    private ProcessedMessageService service;

    @Test
    void firstDeliveryIsProcessed() {
        when(processedMessageRepo.insertIfAbsent("msg-1", "default_share_account_queue")).thenReturn(1);

        assertThat(service.markFirstDelivery("msg-1", "default_share_account_queue")).isTrue();
    }

    @Test
    void duplicateDeliveryIsSkipped() {
        when(processedMessageRepo.insertIfAbsent("msg-1", "default_share_account_queue")).thenReturn(0);

        assertThat(service.markFirstDelivery("msg-1", "default_share_account_queue")).isFalse();
    }

    @Test
    void messageWithoutIdIsProcessedWithoutCheck() {
        assertThat(service.markFirstDelivery(null, "kyc_verification_queue")).isTrue();
        verifyNoInteractions(processedMessageRepo);
    }
}
