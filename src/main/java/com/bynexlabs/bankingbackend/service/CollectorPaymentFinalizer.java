package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.CollectorRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Applies collector payments whose 10-minute correction window has closed (D39). Runs every
 * app.collector.finalize-interval-ms (default one minute), so a payment is applied between 10
 * and about 11 minutes after it was saved.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorPaymentFinalizer {

    private static final int BATCH_SIZE = 100;

    private final TransactionRepo transactionRepo;

    private final CollectorPaymentApplier applier;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    @Scheduled(fixedDelayString = "${app.collector.finalize-interval-ms:60000}",
            initialDelayString = "${app.collector.finalize-interval-ms:60000}")
    public void finaliseDuePayments() {
        LocalDateTime windowClosedBefore = LocalDateTime.now(clock).minus(CollectorRules.CORRECTION_WINDOW);
        List<UUID> due = transactionRepo.findDuePendingCollectorPayments(windowClosedBefore, BATCH_SIZE);
        for (UUID id : due) {
            try {
                applier.finaliseDue(id);
            } catch (RuntimeException e) {
                // e.g. the ledger rules are broken: leave it PENDING and try again next run.
                log.error("Could not finalise collector payment {}: {}", id, e.getMessage());
            }
        }
    }
}
