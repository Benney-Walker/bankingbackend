package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.MemberSmsEvent;
import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;


import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberSmsSenderTest {

    @Mock
    private SmsInterface smsInterface;

    @Mock
    private ProcessedMessageService processedMessageService;

    @InjectMocks
    private MemberSmsSender sender;

    private final MemberSmsEvent event = MemberSmsEvent.builder()
            .memberId(java.util.UUID.randomUUID()).phoneNumber("0241234567").message("Your member details have been updated.").build();

    @Test
    void sendsThenRecordsTheDelivery() {
        when(smsInterface.sendSms("Your member details have been updated.", "0241234567"))
                .thenReturn(SmsResponse.builder().success(true).build());

        sender.send(event, "msg-1");

        InOrder order = inOrder(processedMessageService, smsInterface);
        order.verify(processedMessageService).isProcessed("msg-1", MemberSmsSender.CONSUMER);
        order.verify(smsInterface).sendSms("Your member details have been updated.", "0241234567");
        order.verify(processedMessageService).recordDelivered("msg-1", MemberSmsSender.CONSUMER);
    }

    @Test
    void duplicateDeliveryIsNotTextedAgain() {
        when(processedMessageService.isProcessed("msg-1", MemberSmsSender.CONSUMER)).thenReturn(true);

        sender.send(event, "msg-1");

        verifyNoInteractions(smsInterface);
        verify(processedMessageService, never()).recordDelivered(any(), any());
    }

    // Throwing makes RabbitMQ retry, then dead-letter the message.
    @Test
    void refusedSmsIsRetriedAndNotRecorded() {
        when(smsInterface.sendSms(any(), any())).thenReturn(SmsResponse.builder().success(false).build());

        assertThatThrownBy(() -> sender.send(event, "msg-1")).isInstanceOf(IllegalStateException.class);
        verify(processedMessageService, never()).recordDelivered(any(), any());
    }
}
