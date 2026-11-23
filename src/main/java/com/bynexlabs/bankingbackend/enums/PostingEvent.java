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
    COLLECTOR_SHARE_PURCHASE,
    /** A collector's cash counted in at the office when their daily submission is approved. */
    COLLECTOR_HANDOVER,
    /** A collector paying back a shortage at the office: 1015 to 1010. */
    COLLECTOR_SHORTAGE_REPAYMENT
}
