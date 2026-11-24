package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.LoanAccountRepo;
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
 * Takes due loan installments once a day (owner decisions 2026-11-24). Runs late in the evening
 * (app.loan.repayment-cron, default 23:30 Africa/Accra) so the day's susu collections are already
 * in the members' accounts. Each loan is handled in its own transaction, so one failure doesn't
 * stop the rest.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanRepaymentJob {

    private final LoanAccountRepo loanAccountRepo;

    private final LoanRepaymentService repaymentService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    @Scheduled(cron = "${app.loan.repayment-cron:0 30 23 * * *}", zone = "Africa/Accra")
    public void collectDueInstallments() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<UUID> loans = loanAccountRepo.findIdsWithInstallmentsDue(now.toLocalDate());
        int failed = 0;
        for (UUID loanId : loans) {
            try {
                repaymentService.collect(loanId, now);
            } catch (RuntimeException e) {
                failed++;
                log.error("Loan {}: due installments couldn't be collected; will try again on the next run", loanId, e);
            }
        }
        if (!loans.isEmpty()) {
            log.info("Loan installments: {} loan(s) with installments due, {} failed", loans.size(), failed);
        }
    }
}
