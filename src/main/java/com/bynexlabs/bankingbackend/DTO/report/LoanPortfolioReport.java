package com.bynexlabs.bankingbackend.DTO.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Loans being repaid right now (owner decision 2026-11-24), with what's paid, outstanding and
 * overdue (totals: borrowed money plus interest), and the next due date.
 */
public record LoanPortfolioReport(LocalDateTime asOf, UUID branchId, String branchName, List<Loan> loans,
                                  int activeLoans, int overdueLoans, BigDecimal amountLent, BigDecimal outstanding,
                                  BigDecimal overdue) {

    public record Loan(UUID loanId, UUID memberId, String memberName, String branchName, String productName,
                       LocalDate disbursementDate, BigDecimal amount, BigDecimal totalToRepay, BigDecimal paid,
                       BigDecimal outstanding, BigDecimal overdue, LocalDate nextDueDate) {
    }
}
