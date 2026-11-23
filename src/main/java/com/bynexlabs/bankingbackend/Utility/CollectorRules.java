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

/** Rules for collector payments. */
public final class CollectorRules {

    /**
     * How far ahead of the server clock a phone time may be. The app keeps a payment for 5
     * minutes before sending it (owner decision 2026-11-23), so its times are normally in the past.
     */
    private static final Duration DEVICE_CLOCK_TOLERANCE = Duration.ofMinutes(5);

    private CollectorRules() {
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

    /** A location point's phone time can't be in the future. */
    public static void requireValidRecordedAt(LocalDateTime recordedAt, LocalDateTime now) {
        if (recordedAt != null && recordedAt.isAfter(now.plus(DEVICE_CLOCK_TOLERANCE))) {
            throw new BadRequestException("recordedAt can't be in the future");
        }
    }

    /** The phone's createdAt and updatedAt: not in the future, and the edit not before the creation. */
    public static void requireValidPhoneTimes(LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime now) {
        if (createdAt == null || updatedAt == null) {
            throw new BadRequestException("createdAt and updatedAt are required");
        }
        LocalDateTime latest = now.plus(DEVICE_CLOCK_TOLERANCE);
        if (createdAt.isAfter(latest) || updatedAt.isAfter(latest)) {
            throw new BadRequestException("createdAt and updatedAt can't be in the future. Check the phone's date and time");
        }
        if (updatedAt.isBefore(createdAt)) {
            throw new BadRequestException("updatedAt can't be before createdAt");
        }
    }
}
