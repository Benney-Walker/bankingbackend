package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.OutboxEventRepo;
import com.bynexlabs.bankingbackend.models.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entry point for sending a RabbitMQ message that must not be lost (decision D6). The message is
 * stored in the caller's database transaction, so it exists if and only if the business change
 * committed, and is handed to OutboxPublisher once that transaction commits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxService {

    private final OutboxEventRepo outboxEventRepo;

    private final MessageConverter messageConverter;

    private final OutboxPublisher outboxPublisher;

    // MANDATORY: calling this outside a transaction would break the all-or-nothing guarantee,
    // so Spring rejects it instead of silently opening a separate transaction.
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String exchange, String routingKey, Object event) {
        // Convert now with the same converter RabbitMQ sending uses, so the stored payload is
        // byte-for-byte what consumers would have received from a direct send.
        Message message = messageConverter.toMessage(event, new MessageProperties());
        MessageProperties properties = message.getMessageProperties();
        Object typeId = properties.getHeaders().get(OutboxPublisher.TYPE_ID_HEADER);

        OutboxEvent saved = outboxEventRepo.save(OutboxEvent.builder()
                .exchange(exchange)
                .routingKey(routingKey)
                .payload(new String(message.getBody(), OutboxPublisher.charsetOf(properties.getContentEncoding())))
                .contentType(properties.getContentType())
                .contentEncoding(properties.getContentEncoding())
                .typeId(typeId == null ? null : typeId.toString())
                .createdAt(LocalDateTime.now())
                .attempts(0)
                .build());

        UUID outboxId = saved.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    outboxPublisher.publishById(outboxId);
                } catch (RuntimeException e) {
                    // The request has already succeeded; the retry job will send it.
                    log.warn("Immediate publish of outbox event {} failed; it will be retried", outboxId, e);
                }
            }
        });
    }
}
