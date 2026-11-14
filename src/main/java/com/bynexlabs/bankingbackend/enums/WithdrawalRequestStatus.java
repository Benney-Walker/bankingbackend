package com.bynexlabs.bankingbackend.enums;

/** Lifecycle of a collector's withdrawal request. Stored by name. */
public enum WithdrawalRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    PAID
}
