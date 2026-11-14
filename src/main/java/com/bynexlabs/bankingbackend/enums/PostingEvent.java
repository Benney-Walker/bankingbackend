package com.bynexlabs.bankingbackend.enums;

/** The business events posting rules are written for (posting_rule.event). Stored by name. */
public enum PostingEvent {
    DEPOSIT,
    /** A member's first deposit, part of which buys the mandatory shares. */
    FIRST_DEPOSIT,
    SHARE_PURCHASE,
    WITHDRAWAL,
    TRANSFER,
    /** A collector's savings deposit, applied when its correction window closes. */
    COLLECTOR_DEPOSIT,
    /** A collector's share purchase, applied when its correction window closes. */
    COLLECTOR_SHARE_PURCHASE
}
