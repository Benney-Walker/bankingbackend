package com.bynexlabs.bankingbackend.enums;

/** Business actions recorded in audit_log (roadmap 8.1). Stored by name, so never rename one. */
public enum AuditAction {
    STAFF_CREATED,
    STAFF_PROFILE_UPDATED,
    STAFF_ROLES_CHANGED,
    STAFF_DEACTIVATED,
    STAFF_REACTIVATED,
    STAFF_PASSWORD_RESET,
    MEMBER_REGISTERED,
    MEMBER_CONTACT_UPDATED,
    KYC_SUBMITTED,
    KYC_RESUBMITTED,
    KYC_AUTO_VERIFIED,
    KYC_SENT_TO_MANUAL_REVIEW,
    KYC_APPROVED,
    KYC_REJECTED,
    KYC_DOCUMENT_VIEWED
}
