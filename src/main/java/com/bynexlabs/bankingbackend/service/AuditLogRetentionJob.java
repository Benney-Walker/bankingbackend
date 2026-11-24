package com.bynexlabs.bankingbackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Deletes audit log entries older than 7 years, once a day (owner decision 2026-11-24). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogRetentionJob {

    private final AuditLogService auditLogService;

    @Scheduled(cron = "${app.audit.retention-cron:0 15 4 * * *}", zone = "Africa/Accra")
    public void deleteExpiredEntries() {
        int deleted = auditLogService.deleteExpired();
        if (deleted > 0) {
            log.info("Audit log retention: deleted {} entries older than {} years", deleted, AuditLogService.RETENTION_YEARS);
        }
    }
}
