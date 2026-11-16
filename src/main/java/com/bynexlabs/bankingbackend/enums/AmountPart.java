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
    REMAINDER,
    /** The part of a collector handover that settles recorded collections (at most the expected amount). */
    SETTLED,
    /** Cash counted in a collector handover above the expected amount. */
    OVERAGE
}
