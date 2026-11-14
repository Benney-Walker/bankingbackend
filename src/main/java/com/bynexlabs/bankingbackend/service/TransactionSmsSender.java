package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.TransactionSmsEvent;
import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Sends queued transaction SMS (consumer of transaction_sms_queue).
 *
 * The SMS is an external call, so it can't share a database transaction with the duplicate
 * record: the message is checked first, sent, and only then marked as delivered. If the provider
 * refuses or can't be reached, the exception makes RabbitMQ retry (3 times), after which the
 * message moves to transaction_sms_dead_letter_queue. A crash between sending and marking can
 * send the same SMS twice; that's preferred to the member missing it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSmsSender {

    static final String CONSUMER = RabbitMQConfig.TRANSACTION_SMS_QUEUE_NAME;

    private final SmsInterface smsService;

    private final ProcessedMessageService processedMessageService;

    public void send(TransactionSmsEvent event, String messageId) {
        if (processedMessageService.isProcessed(messageId, CONSUMER)) {
            log.info("Skipping duplicate transaction SMS {} for transaction {}", messageId, event.getTransactionId());
            return;
        }
        SmsResponse response = smsService.sendSms(event.getMessage(), event.getPhoneNumber());
        if (response == null || !response.isSuccess()) {
            throw new IllegalStateException("SMS provider didn't accept the transaction SMS for transaction "
                    + event.getTransactionId());
        }
        processedMessageService.recordDelivered(messageId, CONSUMER);
    }
}
