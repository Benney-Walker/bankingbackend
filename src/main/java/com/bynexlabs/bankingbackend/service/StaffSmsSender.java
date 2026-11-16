package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.StaffSmsEvent;
import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends queued new-staff SMS (consumer of staff_sms_queue), the same way as transaction SMS:
 * checked for duplicates, sent, then marked as delivered. A refusal or unreachable provider makes
 * RabbitMQ retry (3 times), then the message moves to staff_sms_dead_letter_queue.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaffSmsSender {

    static final String CONSUMER = RabbitMQConfig.STAFF_SMS_QUEUE_NAME;

    private final SmsInterface smsService;

    private final ProcessedMessageService processedMessageService;

    public void send(StaffSmsEvent event, String messageId) {
        if (processedMessageService.isProcessed(messageId, CONSUMER)) {
            log.info("Skipping duplicate staff SMS {} for staff {}", messageId, event.getStaffId());
            return;
        }
        SmsResponse response = smsService.sendSms(event.getMessage(), event.getPhoneNumber());
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("SMS provider didn't accept the new-staff SMS for staff " + event.getStaffId());
        }
        processedMessageService.recordDelivered(messageId, CONSUMER);
    }
}
