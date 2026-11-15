package com.bynexlabs.bankingbackend.enums;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    LOAN_PAYMENT,
    FEE,
    TRANSFER,
    SHARES,
    /** Cash a collector hands in at the office when their daily submission is approved (V39). No member account. */
    COLLECTOR_HANDOVER
}
