package com.bynexlabs.bankingbackend.enums;

/**
 * Which kind of collector record a correction changed. Stored by name. Only payments since
 * withdrawal requests were removed (owner decision 2026-11-15); kept so another kind of record
 * can be added later without changing the column.
 */
public enum CorrectionRecordType {
    PAYMENT
}
