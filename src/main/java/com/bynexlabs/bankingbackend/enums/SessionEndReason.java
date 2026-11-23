package com.bynexlabs.bankingbackend.enums;

/** Why a refresh token stopped working (refresh_token.revoke_reason, V39). Stored by name. */
public enum SessionEndReason {
    /** The staff member signed out on this device. */
    LOGOUT,
    /** A token that had already been swapped was used again: a sign it was copied, so the whole session ends. */
    REUSE_DETECTED,
    /** Replaced when the app retried a refresh whose response it never received. */
    SUPERSEDED,
    /** The collector's daily cash submission was approved. */
    DAILY_APPROVAL,
    PASSWORD_RESET,
    DEACTIVATED,
    ROLES_CHANGED,
    /** The collector's registered phone was released (V42). */
    DEVICE_RELEASED
}
