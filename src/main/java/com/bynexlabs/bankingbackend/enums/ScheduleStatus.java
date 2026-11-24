package com.bynexlabs.bankingbackend.enums;

/**
 * One monthly installment (V46): PENDING until its due date, PAID once fully collected, OVERDUE
 * when due and not fully collected (the daily job keeps collecting; no penalty).
 */
public enum ScheduleStatus {
    PENDING,
    PAID,
    OVERDUE
}
