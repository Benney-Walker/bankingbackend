package com.bynexlabs.bankingbackend.enums;

/** How a transaction was recorded. Stored by name. */
public enum TransactionChannel {
    OFFICE,
    COLLECTOR,
    /** Done by the backend itself, e.g. monthly loan installments (V46). */
    SYSTEM
}
