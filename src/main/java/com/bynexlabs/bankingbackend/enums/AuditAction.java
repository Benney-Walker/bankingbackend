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
    KYC_DOCUMENT_VIEWED,
    FINANCIAL_SETTINGS_UPDATED,
    MEMBER_UPDATE_REQUESTED,
    MEMBER_UPDATE_APPROVED,
    MEMBER_UPDATE_REJECTED,
    COLLECTOR_DEVICE_REGISTERED,
    COLLECTOR_DEVICE_RELEASED,
    /** A staff member generated an account statement (owner decision 2026-11-23). */
    ACCOUNT_STATEMENT_GENERATED,
    LOAN_PRODUCT_CREATED,
    LOAN_PRODUCT_UPDATED,
    LOAN_APPLICATION_SUBMITTED,
    /** An administrator approved a loan; it was paid out at once. */
    LOAN_APPROVED,
    LOAN_REJECTED
}
