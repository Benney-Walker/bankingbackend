package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.Repository.PasswordResetCodeRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import com.bynexlabs.bankingbackend.models.PasswordResetCode;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Staff self-service password reset (roadmap 1.5c). Owner rules, decided 2026-11-07:
 * the staff member enters their staff ID; a 6-digit code goes to their phone by SMS (email
 * later, once a provider is chosen); the code is valid for 10 minutes and allows 3 wrong
 * attempts; only ACTIVE staff can reset; administrators cannot reset other people's passwords.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration CODE_VALIDITY = Duration.ofMinutes(10);
    // Owner rule: at most one code per staff ID per minute, so the request endpoint can't be
    // used to run up SMS costs or flood a staff member's phone.
    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    static final int MAX_FAILED_ATTEMPTS = 3;
    static final String INVALID_CODE_MESSAGE = "Invalid or expired reset code";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffRepo staffRepo;

    private final PasswordResetCodeRepo resetCodeRepo;

    private final BCryptPasswordEncoder passwordEncoder;

    private final SmsInterface smsService;

    private final AuditLogService auditLogService;

    /**
     * Issues a new code and texts it to the staff member. Deliberately not transactional: the
     * code is saved first, and the SMS call happens after that commit, outside any transaction.
     * The caller always gets the same response, so this endpoint can't be used to discover
     * which staff IDs exist or are active.
     */
    public void requestReset(String staffId) {
        Staff staff = staffRepo.findByStaffId(staffId)
                .filter(found -> found.getStaffStatus() == StaffStatus.ACTIVE)
                .orElse(null);
        if (staff == null) {
            log.info("Password reset requested for unknown or inactive staff ID {}", staffId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // One pending code per staff member: a new request replaces the previous code, unless
        // that code was issued within the cooldown, in which case nothing is sent.
        PasswordResetCode resetCode = resetCodeRepo.findByStaff_Id(staff.getId())
                .orElseGet(() -> PasswordResetCode.builder().staff(staff).build());
        if (resetCode.getCreatedAt() != null && resetCode.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            log.info("Password reset for staff {} requested again within the cooldown; no new code sent", staffId);
            return;
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        resetCode.setCodeHash(passwordEncoder.encode(code));
        resetCode.setExpiresAt(now.plus(CODE_VALIDITY));
        resetCode.setFailedAttempts(0);
        resetCode.setCreatedAt(now);
        resetCodeRepo.save(resetCode);

        SmsResponse sms = smsService.sendSms(resetMessage(code), staff.getPhoneNumber());
        if (sms == null || !sms.isSuccess()) {
            // The code is saved; the staff member can simply request again.
            log.warn("Password reset SMS could not be sent for staff {}", staffId);
        }
    }

    /**
     * Checks the code and sets the new password. Wrong attempts are counted and committed even
     * though the request fails, hence noRollbackFor; the code row is locked while it is checked.
     */
    @Transactional(noRollbackFor = BadRequestException.class)
    public void confirmReset(String staffId, String code, String newPassword) {
        Staff staff = staffRepo.findByStaffId(staffId)
                .filter(found -> found.getStaffStatus() == StaffStatus.ACTIVE)
                .orElseThrow(() -> new BadRequestException(INVALID_CODE_MESSAGE));
        PasswordResetCode resetCode = resetCodeRepo.lockByStaffId(staff.getId())
                .orElseThrow(() -> new BadRequestException(INVALID_CODE_MESSAGE));

        if (resetCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            resetCodeRepo.delete(resetCode);
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }

        if (!passwordEncoder.matches(code, resetCode.getCodeHash())) {
            int attempts = resetCode.getFailedAttempts() + 1;
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                // Used up: the staff member must request a fresh code.
                resetCodeRepo.delete(resetCode);
            } else {
                resetCode.setFailedAttempts(attempts);
                resetCodeRepo.save(resetCode);
            }
            throw new BadRequestException(INVALID_CODE_MESSAGE);
        }

        staff.setPassword(passwordEncoder.encode(newPassword));
        staff.setUpdatedAt(LocalDateTime.now());
        staffRepo.save(staff);
        resetCodeRepo.delete(resetCode);
        // The staff member resets their own password; nobody is logged in during a reset.
        auditLogService.recordAs(staffId, AuditAction.STAFF_PASSWORD_RESET, AuditTargetType.STAFF, staffId, "Reset with SMS code");
        log.info("Password reset completed for staff {}", staffId);
    }

    // Wording approved by the owner (2026-11-07).
    static String resetMessage(String code) {
        return "Your password reset code is " + code + ". It expires in "
                + CODE_VALIDITY.toMinutes() + " minutes. Do not share it with anyone.";
    }
}
