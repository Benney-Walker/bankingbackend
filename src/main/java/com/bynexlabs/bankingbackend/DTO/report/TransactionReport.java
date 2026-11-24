package com.bynexlabs.bankingbackend.DTO.report;

import com.bynexlabs.bankingbackend.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Completed money movements for a date range (owner decision 2026-11-24): one row per day, branch
 * and transaction type, then totals by type. A transaction belongs to its member's branch; a
 * collector handover (no member account) to the collector's branch.
 */
public record TransactionReport(LocalDate from, LocalDate to, UUID branchId, String branchName, List<Row> rows,
                                List<TypeTotal> totals, long count, BigDecimal amount, BigDecimal fees) {

    public record Row(LocalDate date, UUID branchId, String branchName, TransactionType type, long count,
                      BigDecimal amount, BigDecimal fees) {
    }

    public record TypeTotal(TransactionType type, long count, BigDecimal amount, BigDecimal fees) {
    }
}
