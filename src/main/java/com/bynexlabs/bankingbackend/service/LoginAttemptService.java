package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Sign-in lockout and sign-in audit (owner decisions 2026-11-24):
 * - 5 wrong passwords in a row lock a staff or collector account; a locked account can't sign in
 *   (CustomUserDetailsService) until an administrator unlocks it. A correct password starts the
 *   count again. Password reset doesn't unlock;
 * - successful and failed sign-ins are audited. A failed attempt is logged against the account it
 *   named, with no actor, since nobody was verified. Attempts naming an unknown staff ID aren't
 *   logged, so whatever was typed (sometimes a password) isn't stored.
 *
 * The sign-in itself runs outside a transaction, so each write here commits on its own and is kept
 * even though the sign-in fails.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    /** Owner decision 2026-11-24. */
    static final int MAX_FAILED_ATTEMPTS = 5;

    private final StaffRepo staffRepo;

    private final AuditLogService auditLogService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void wrongPassword(String staffId) {
        Optional<Staff> found = staffId == null ? Optional.empty() : staffRepo.findByStaffId(staffId.trim());
        if (found.isEmpty()) {
            log.warn("Failed sign-in for an unknown staff ID");
            return;
        }
        Staff staff = found.get();
        if (staffRepo.recordFailedLogin(staff.getId(), MAX_FAILED_ATTEMPTS, LocalDateTime.now(clock)) == 0) {
            return;
        }
        int attempts = staffRepo.findFailedLoginAttempts(staff.getId());
        auditLogService.recordAs(null, AuditAction.LOGIN_FAILED, AuditTargetType.STAFF, staff.getStaffId(),
                "Wrong password (" + attempts + " of " + MAX_FAILED_ATTEMPTS + " in a row)");
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            log.warn("Staff {} locked after {} wrong passwords", staff.getStaffId(), attempts);
            auditLogService.recordAs(null, AuditAction.STAFF_LOCKED, AuditTargetType.STAFF, staff.getStaffId(),
                    "Locked after " + MAX_FAILED_ATTEMPTS + " wrong passwords in a row");
        }
    }

    /** A sign-in refused before the password counted: the account is locked or not active. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refused(String staffId, String reason) {
        Optional<Staff> found = staffId == null ? Optional.empty() : staffRepo.findByStaffId(staffId.trim());
        found.ifPresent(staff -> auditLogService.recordAs(null, AuditAction.LOGIN_FAILED, AuditTargetType.STAFF,
                staff.getStaffId(), reason));
    }

    /** The password was right: the count starts again. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void passwordAccepted(Staff staff) {
        staffRepo.clearFailedLogins(staff.getId());
    }

    /** Tokens were issued. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void signedIn(Staff staff, String app) {
        auditLogService.recordAs(staff.getStaffId(), AuditAction.LOGIN_SUCCEEDED, AuditTargetType.STAFF, staff.getStaffId(),
                "Signed in to the " + app);
    }

    /** Administrators unlock a locked account (owner decision 2026-11-24). */
    @Transactional
    public StaffResponse unlock(String staffId, String adminStaffId) {
        Staff staff = staffRepo.findByStaffId(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
        if (staffRepo.unlock(staff.getId()) == 0) {
            throw new ConflictException("This account isn't locked");
        }
        auditLogService.recordAs(adminStaffId, AuditAction.STAFF_UNLOCKED, AuditTargetType.STAFF, staff.getStaffId(),
                "Unlocked");
        StaffResponse response = StaffResponse.from(staff);
        response.setLockedAt(null);
        return response;
    }
}
