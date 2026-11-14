package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;

import java.time.Duration;
import java.time.LocalDateTime;

/** Rules shared by collector payments and withdrawal requests. */
public final class CollectorRules {

    /**
     * Owner decision 2026-11-07: a collector can correct a record for 10 minutes after the backend
     * saves it. The collector app counts the same 10 minutes for offline records, so the two must
     * stay in step.
     */
    public static final Duration CORRECTION_WINDOW = Duration.ofMinutes(10);

    /** How far ahead of the server clock an offline record's device time may be. */
    private static final Duration DEVICE_CLOCK_TOLERANCE = Duration.ofMinutes(5);

    private CollectorRules() {
    }

    /** When the window closes, or null for offline records, which arrive final (D40). */
    public static LocalDateTime correctableUntil(LocalDateTime createdAt, LocalDateTime recordedAt) {
        return recordedAt != null || createdAt == null ? null : createdAt.plus(CORRECTION_WINDOW);
    }

    public static boolean windowOpen(LocalDateTime createdAt, LocalDateTime recordedAt, LocalDateTime now) {
        LocalDateTime until = correctableUntil(createdAt, recordedAt);
        return until != null && now.isBefore(until);
    }

    // Owner decision 2026-11-14: a collector works with any member in their own branch.
    public static void requireSameBranch(Staff collector, MemberAccount account) {
        if (collector.getBranch() == null || account.getMember().getBranch() == null
                || !collector.getBranch().getId().equals(account.getMember().getBranch().getId())) {
            throw new ForbiddenException("This member isn't in your branch");
        }
    }

    public static void requireActive(MemberAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE
                || account.getMember().getMembershipStatus() != MembershipStatus.ACTIVE) {
            throw new ConflictException("Account " + account.getAccountNumber() + " is not active");
        }
    }

    public static void requireValidRecordedAt(LocalDateTime recordedAt, LocalDateTime now) {
        if (recordedAt != null && recordedAt.isAfter(now.plus(DEVICE_CLOCK_TOLERANCE))) {
            throw new BadRequestException("recordedAt can't be in the future");
        }
    }
}
