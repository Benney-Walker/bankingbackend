package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
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
 * Clean-up for the old 10-minute window (D39). Since 2026-11-23 collector payments are applied on
 * arrival and never left PENDING, but payments saved PENDING before that change still need
 * applying. Runs every app.collector.finalize-interval-ms (default one minute) and applies any
 * that remain; once there are none it finds nothing to do.
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
        List<UUID> due = transactionRepo.findDuePendingCollectorPayments(LocalDateTime.now(clock), BATCH_SIZE);
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
