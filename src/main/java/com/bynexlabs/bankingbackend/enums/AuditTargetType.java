package com.bynexlabs.bankingbackend.enums;

/** The kind of record an audit entry is about. Stored by name, so never rename one. */
public enum AuditTargetType {
    /** target_id is the staff code, e.g. 900100. */
    STAFF,
    /** target_id is the member's UUID. KYC actions use the member, since there's one KYC record per member. */
    MEMBER,
    /** target_id names the settings group, e.g. "financial". */
    SETTINGS
}
