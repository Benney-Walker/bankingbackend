package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.OutboxEventRepo;
import com.bynexlabs.bankingbackend.models.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sends outbox rows to RabbitMQ. Two paths, same rules:
 * - publishById: called right after the business transaction commits, so normally a message
 *   goes out immediately, as fast as a direct send.
 * - publishPending: a scheduled safety net that retries anything still unsent, e.g. because
 *   RabbitMQ was down. Interval: app.outbox.retry-interval-ms.
 * Rows are locked with SKIP LOCKED, so two paths never send the same row concurrently.
 * Delivery is at-least-once: if the broker accepts a message but the "published" update then
 * fails, the row is sent again later, so consumers must tolerate duplicates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisher {

    static final int BATCH_SIZE = 50;

    // Header the AMQP Jackson converter uses to name the payload class.
    static final String TYPE_ID_HEADER = "__TypeId__";

    private static final int MAX_ERROR_LENGTH = 2000;

    private final OutboxEventRepo outboxEventRepo;

    private final RabbitTemplate rabbitTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void publishById(UUID id) {
        outboxEventRepo.lockUnpublishedById(id).ifPresent(this::send);
    }

    @Scheduled(fixedDelayString = "${app.outbox.retry-interval-ms}",
               initialDelayString = "${app.outbox.retry-interval-ms}")
    @Transactional
    public void publishPending() {
        for (OutboxEvent event : outboxEventRepo.lockUnpublishedBatch(BATCH_SIZE)) {
            if (!send(event)) {
                // The broker is most likely unavailable; stop here rather than wait on a
                // timeout for every remaining row. The next run tries again.
                break;
            }
        }
    }

    boolean send(OutboxEvent event) {
        event.setAttempts(event.getAttempts() + 1);
        try {
            rabbitTemplate.send(event.getExchange(), event.getRoutingKey(), toMessage(event));
            event.setPublishedAt(LocalDateTime.now());
            event.setLastError(null);
            outboxEventRepo.save(event);
            return true;
        } catch (AmqpException e) {
            event.setLastError(truncate(e.getMessage()));
            outboxEventRepo.save(event);
            log.warn("Outbox event {} not published (attempt {}): {}", event.getId(), event.getAttempts(), e.getMessage());
            return false;
        }
    }

    static Message toMessage(OutboxEvent event) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(event.getContentType());
        properties.setContentEncoding(event.getContentEncoding());
        if (event.getTypeId() != null) {
            properties.setHeader(TYPE_ID_HEADER, event.getTypeId());
        }
        // The outbox ID doubles as the message ID, so consumers can recognise redeliveries.
        properties.setMessageId(event.getId().toString());
        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        return new Message(event.getPayload().getBytes(charsetOf(event.getContentEncoding())), properties);
    }

    static Charset charsetOf(String contentEncoding) {
        return contentEncoding == null ? StandardCharsets.UTF_8 : Charset.forName(contentEncoding);
    }

    private static String truncate(String error) {
        if (error == null) {
            return null;
        }
        return error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH);
    }
}
