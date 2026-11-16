package com.bynexlabs.bankingbackend.enums;

/** How the withdrawal fee is worked out. Chosen by administrators in the financial settings. */
public enum WithdrawalFeeType {
    /** A fixed amount in GHS per withdrawal. */
    FLAT,
    /** A percentage of the amount withdrawn (1.5 means 1.5%). */
    PERCENTAGE
}
