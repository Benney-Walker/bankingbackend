package com.bynexlabs.bankingbackend.enums;

/** A collector's daily submission (V39). Stored by name. */
public enum CollectorSubmissionStatus {
    /** The collector closed the day; waiting for the branch's agent or an administrator. */
    SUBMITTED,
    /** Cash counted and accepted; final. */
    APPROVED
}
