package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.ProcessedMessageRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes RabbitMQ consumers safe against duplicate deliveries (roadmap 2.2). Call it first,
 * inside the consumer's transaction: the record and the consumer's work commit or roll back
 * together, so a failed attempt doesn't mark the message as done.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessedMessageService {

    private final ProcessedMessageRepo processedMessageRepo;

    /** @return true the first time this consumer sees the message, false for a duplicate. */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markFirstDelivery(String messageId, String consumer) {
        if (messageId == null) {
            // Only outbox messages carry an ID; without one there's nothing to de-duplicate on.
            log.debug("Message for {} has no message ID; processing without duplicate check", consumer);
            return true;
        }
        boolean first = processedMessageRepo.insertIfAbsent(messageId, consumer) == 1;
        if (!first) {
            log.info("Skipping duplicate delivery of message {} to {}", messageId, consumer);
        }
        return first;
    }

    /**
     * For consumers whose work is an external call that can't join a database transaction (an
     * SMS): check first, do the work, then record it with recordDelivered.
     */
    @Transactional(readOnly = true)
    public boolean isProcessed(String messageId, String consumer) {
        return messageId != null && processedMessageRepo.isProcessed(messageId, consumer);
    }

    @Transactional
    public void recordDelivered(String messageId, String consumer) {
        if (messageId != null) {
            processedMessageRepo.insertIfAbsent(messageId, consumer);
        }
    }
}
