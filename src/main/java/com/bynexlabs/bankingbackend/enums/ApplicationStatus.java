package com.bynexlabs.bankingbackend.enums;

/**
 * A loan application (V46). PENDING until an administrator reviews it; APPROVED means the loan was
 * paid out at once (owner decision 2026-11-24); REJECTED carries the reason. Final once reviewed.
 */
public enum ApplicationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
