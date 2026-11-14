package com.bynexlabs.bankingbackend.DTO.ledger;

import java.math.BigDecimal;
import java.util.List;

/**
 * Debit and credit totals for every postable GL account. balance is shown on the account's
 * normal side (debit for assets and expenses, credit for liabilities, equity and revenue), so
 * a positive number is the usual case. balanced is false only if the ledger is broken.
 */
public record TrialBalanceResponse(List<Line> accounts, BigDecimal totalDebits, BigDecimal totalCredits, boolean balanced) {

    public record Line(String accountCode, String name, String category,
                       BigDecimal totalDebits, BigDecimal totalCredits, BigDecimal balance) {
    }
}
