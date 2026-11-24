package com.bynexlabs.bankingbackend.enums;

public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    /** A loan installment, or part of one, taken from the member's account by the daily job (V46). */
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
    COLLECTOR_SHORTAGE_REPAYMENT,
    /** An approved loan paid into the member's account, less the processing fee (V46). */
    LOAN_DISBURSEMENT
}
