package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.MemberSmsEvent;
import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends queued member notification SMS (consumer of member_sms_queue), the same way as
 * transaction SMS: checked for duplicates, sent, then marked as delivered. A refusal or
 * unreachable provider makes RabbitMQ retry (3 times), then the message moves to
 * member_sms_dead_letter_queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemberSmsSender {

    static final String CONSUMER = RabbitMQConfig.MEMBER_SMS_QUEUE_NAME;

    private final SmsInterface smsService;

    private final ProcessedMessageService processedMessageService;

    public void send(MemberSmsEvent event, String messageId) {
        if (processedMessageService.isProcessed(messageId, CONSUMER)) {
            log.info("Skipping duplicate member SMS {} for member {}", messageId, event.getMemberId());
            return;
        }
        SmsResponse response = smsService.sendSms(event.getMessage(), event.getPhoneNumber());
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("SMS provider didn't accept the member SMS for member " + event.getMemberId());
        }
        processedMessageService.recordDelivered(messageId, CONSUMER);
    }
}
