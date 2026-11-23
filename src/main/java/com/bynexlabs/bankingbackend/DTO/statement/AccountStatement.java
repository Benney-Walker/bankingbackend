package com.bynexlabs.bankingbackend.DTO.statement;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything printed on an account statement (owner decisions 2026-11-23): the header (credit
 * union, branch, member and account), opening and closing balances, money in and out for the
 * period, and one line per movement with the running balance.
 */
public record AccountStatement(String creditUnionName, String branchName, String memberName,
                               String accountNumber, String accountType, LocalDate from, LocalDate to,
                               LocalDateTime generatedAt, BigDecimal openingBalance, BigDecimal totalIn,
                               BigDecimal totalOut, BigDecimal closingBalance, List<StatementLine> lines) {
}
