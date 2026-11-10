package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.Repository.PasswordResetCodeRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.InternalServerError;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import com.bynexlabs.bankingbackend.models.PasswordResetCode;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration CODE_VALIDITY = Duration.ofMinutes(10);

    static final Duration RESEND_COOLDOWN = Duration.ofMinutes(1);
    static final int MAX_FAILED_ATTEMPTS = 3;
    static final String INVALID_CODE_MESSAGE = "Invalid or expired reset code";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final StaffRepo staffRepo;

    private final PasswordResetCodeRepo resetCodeRepo;

    private final BCryptPasswordEncoder passwordEncoder;

    private final SmsInterface smsService;

    public void requestReset(String staffId) {
        Staff staff = staffRepo.findByStaffId(staffId)
                .filter(found -> found.getStaffStatus() == StaffStatus.ACTIVE)
                .orElse(null);
        if (staff == null) {
            log.info("Password reset requested for unknown or inactive staff ID {}", staffId);
            throw new BadRequestException("Invalid staff Id or Inactive staff account");
        }

        LocalDateTime now = LocalDateTime.now();

        PasswordResetCode resetCode = resetCodeRepo.findByStaff_Id(staff.getId())
                .orElseGet(() -> PasswordResetCode.builder().staff(staff).build());
        if (resetCode.getCreatedAt() != null && resetCode.getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            log.info("Password reset for staff {} requested again within the cooldown; no new code sent", staffId);
            throw new ConflictException("Password can only be sent once in a minute");
        }

        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        resetCode.setCodeHash(passwordEncoder.encode(code));
        resetCode.setExpiresAt(now.plus(CODE_VALIDITY));
        resetCode.setFailedAttempts(0);
        resetCode.setCreatedAt(now);
        resetCode.setSent(false);

        SmsResponse sms = smsService.sendSms(resetMessage(code), staff.getPhoneNumber());
        if (sms == null || !sms.isSuccess()) {
            log.warn("Password reset SMS could not be sent for staff {}", staffId);
            resetCodeRepo.save(resetCode);
            throw new InternalServerError("Could not send sms! Try again later.");
        }
        resetCode.setSent(true);
        resetCodeRepo.save(resetCode);
    }


    @Transactional(noRollbackFor = BadRequestException.class)
    public ResponseEntity<?> confirmReset(String staffId, String code) {
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
        log.info("Password reset completed for staff {}", staffId);
    }

    // Wording approved by the owner (2026-11-07).
    static String resetMessage(String code) {
        return "Your password reset code is " + code + ". It expires in "
                + CODE_VALIDITY.toMinutes() + " minutes. Do not share it with anyone.";
    }
}
