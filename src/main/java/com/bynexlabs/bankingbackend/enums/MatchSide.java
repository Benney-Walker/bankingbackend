package com.bynexlabs.bankingbackend.enums;

/** Which member account a posting rule's account-type condition looks at. Stored by name. */
public enum MatchSide {
    /** The transaction's member account (the source of a transfer). */
    PRIMARY,
    /** The other account: a transfer's destination, or the shares account of a first deposit. */
    COUNTERPARTY
}
