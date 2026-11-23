package com.bynexlabs.bankingbackend.DTO.collector;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * What a collector owes: shortages recorded on approved submissions, minus repayments.
 * {@code repayments} is filled only when one collector is fetched.
 */
public record CollectorShortageResponse(String collectorStaffId, String collectorName, String branchName,
                                        BigDecimal totalShortage, BigDecimal totalRepaid, BigDecimal outstanding,
                                        List<Repayment> repayments) {

    public record Repayment(UUID transactionId, BigDecimal amount, String receivedByStaffId,
                            LocalDateTime receivedAt, String note) {
    }
}
