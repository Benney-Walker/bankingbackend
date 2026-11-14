package com.bynexlabs.bankingbackend.enums;

/** Which part of a transaction's money a posting rule puts on a GL account. Stored by name. */
public enum AmountPart {
    /** The transaction amount. */
    PRINCIPAL,
    /** The fee charged on top of the amount. */
    FEE,
    /** The share part of a first deposit. */
    SHARE,
    /** A first deposit minus its share part. */
    REMAINDER
}
