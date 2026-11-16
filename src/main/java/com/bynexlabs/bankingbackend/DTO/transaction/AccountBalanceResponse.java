package com.bynexlabs.bankingbackend.DTO.transaction;

import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.models.MemberAccount;

import java.math.BigDecimal;
import java.util.UUID;

/** An account's balances, e.g. for checking before a withdrawal. */
public record AccountBalanceResponse(String accountNumber, AccountType accountType, AccountStatus status,
                                     BigDecimal currentBalance, BigDecimal availableBalance,
                                     UUID memberId, String memberName) {

    /** The account's member must already be loaded. */
    public static AccountBalanceResponse from(MemberAccount account) {
        return new AccountBalanceResponse(
                account.getAccountNumber(),
                account.getAccountType(),
                account.getStatus(),
                account.getCurrentBalance(),
                account.getAvailableBalance(),
                account.getMember().getId(),
                account.getMember().getFirstName() + " " + account.getMember().getLastName());
    }
}
