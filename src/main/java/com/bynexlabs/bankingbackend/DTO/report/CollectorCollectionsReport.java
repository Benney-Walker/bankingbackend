package com.bynexlabs.bankingbackend.DTO.report;

import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Collectors' work for a date range (owner decision 2026-11-24): what each collector recorded each
 * day (dated by the phone's createdAt), and the daily submissions made in the range with the cash
 * expected, counted and any surplus.
 */
public record CollectorCollectionsReport(LocalDate from, LocalDate to, UUID branchId, String branchName,
                                         List<Day> days, List<Submission> submissions, BigDecimal collected,
                                         BigDecimal expected, BigDecimal counted, BigDecimal surplus) {

    public record Day(LocalDate date, String collectorStaffId, String collectorName, String branchName,
                      long payments, BigDecimal amount, long failedPayments, BigDecimal failedAmount) {
    }

    public record Submission(LocalDateTime submittedAt, String collectorStaffId, String collectorName,
                             String branchName, int payments, BigDecimal expected, int failedPayments,
                             BigDecimal counted, BigDecimal surplus, CollectorSubmissionStatus status,
                             String approvedBy, LocalDateTime approvedAt) {
    }
}
