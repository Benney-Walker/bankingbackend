package com.bynexlabs.bankingbackend.enums;

/** How a loan product's processing fee is worked out (V46). */
public enum LoanFeeType {
    /** A fixed GHS amount. */
    FLAT,
    /** A percentage of the approved loan, rounded to 2 decimal places, half up. */
    PERCENTAGE
}
