package com.bynexlabs.bankingbackend.enums;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    LOAN_PAYMENT,
    FEE,
    TRANSFER,
    SHARES,
    /** Cash a collector hands in at the office when their daily submission is approved (V39). No member account. */
    COLLECTOR_HANDOVER,
    /**
     * Retired 2026-11-23 (shortages are no longer allowed, V44). Kept only so any repayment
     * recorded while the feature existed still loads; nothing creates it.
     */
    COLLECTOR_SHORTAGE_REPAYMENT
}
