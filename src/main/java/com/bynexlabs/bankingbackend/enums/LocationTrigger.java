package com.bynexlabs.bankingbackend.enums;

/** Why the collector app took a location point (V41). Stored by name. */
public enum LocationTrigger {
    /** The regular point, every 5 minutes. */
    EVERY_5_MIN,
    /** Taken when a payment was recorded; carries the payment's idempotency key. */
    PAYMENT
}
