package com.bynexlabs.bankingbackend.DTO.loan;

import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import com.bynexlabs.bankingbackend.enums.ScheduleStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A loan with its installments. Amounts owed are totals (borrowed money plus interest): the split
 * between the two is kept for the ledger only (owner decision 2026-11-24). installments is null in
 * lists.
 */
public record LoanResponse(UUID id, UUID applicationId, UUID memberId, String memberName, String accountNumber,
                           String productName, BigDecimal amount, BigDecimal interestRatePercent,
                           BigDecimal totalInterest, BigDecimal totalToRepay, BigDecimal processingFee,
                           Integer termMonths, LocalDate disbursementDate, LocalDate maturityDate,
                           LoanAccountStatus status, BigDecimal paid, BigDecimal outstanding, BigDecimal overdue,
                           List<Installment> installments) {

    public record Installment(int number, LocalDate dueDate, BigDecimal amount, BigDecimal paid,
                              BigDecimal remaining, ScheduleStatus status) {
    }
}
