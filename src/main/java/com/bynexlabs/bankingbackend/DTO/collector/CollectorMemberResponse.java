package com.bynexlabs.bankingbackend.DTO.collector;

import com.bynexlabs.bankingbackend.enums.AccountType;

import java.util.List;

/**
 * What a collector may see of a member (owner decision 2026-11-07): the name and the accounts
 * they can record against, as account number and type. No other member details, no balances.
 */
public record CollectorMemberResponse(String memberName, List<Account> accounts) {

    public record Account(String accountNumber, AccountType accountType) {
    }
}
