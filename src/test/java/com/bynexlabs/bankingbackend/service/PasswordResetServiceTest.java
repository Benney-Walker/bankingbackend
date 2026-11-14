package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.NewPassword;
import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.Repository.PasswordResetCodeRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.InternalServerError;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.interfaces.SmsInterface;
import com.bynexlabs.bankingbackend.models.PasswordResetCode;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Staff password reset in three steps (D31, reworked by the owner): request an SMS code, confirm
 * it for a short-lived reset token, then send the new password with that token.
 */
@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String STAFF_ID = "900105";
    private static final String PHONE = "0201234567";
    private static final String CODE = "482913";
    private static final String RESET_TOKEN = "reset-token";

    @Mock
    private StaffRepo staffRepo;

    @Mock
    private PasswordResetCodeRepo resetCodeRepo;

    @Mock
    private SmsInterface smsService;

    @Mock
    private JwtUtility jwtUtility;

    @Mock
    private StaffService staffService;

    // A real encoder, so the tests prove codes and passwords are stored as BCrypt hashes.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PasswordResetService service;

    private Staff staff;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(staffRepo, resetCodeRepo, passwordEncoder, smsService, jwtUtility, staffService);
        staff = Staff.builder()
                .id(UUID.randomUUID())
                .staffId(STAFF_ID)
                .phoneNumber(PHONE)
                .password(passwordEncoder.encode("old-password-1"))
                .staffStatus(StaffStatus.ACTIVE)
                .build();
    }

    private PasswordResetCode pendingCode(int failedAttempts, LocalDateTime expiresAt) {
        return PasswordResetCode.builder()
                .staff(staff)
                .codeHash(passwordEncoder.encode(CODE))
                .failedAttempts(failedAttempts)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now().minusMinutes(2))
                .build();
    }

    // --- step 1: request a code ---

    @Test
    void unknownStaffIdIsRejected() {
        when(staffRepo.findByStaffId("900999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requestResetCode("900999"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid staff Id or Inactive staff account");
        verifyNoInteractions(resetCodeRepo, smsService);
    }

    @Test
    void inactiveStaffIsRejected() {
        staff.setStaffStatus(StaffStatus.INACTIVE);
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.requestResetCode(STAFF_ID)).isInstanceOf(BadRequestException.class);
        verifyNoInteractions(resetCodeRepo, smsService);
    }

    @Test
    void requestStoresOnlyAHashAndTextsTheSixDigitCode() {
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.findByStaff_Id(staff.getId())).thenReturn(Optional.empty());
        when(smsService.sendSms(anyString(), anyString())).thenReturn(SmsResponse.builder().success(true).build());

        service.requestResetCode(STAFF_ID);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(smsService).sendSms(message.capture(), eq(PHONE));
        Matcher digits = Pattern.compile("\\b(\\d{6})\\b").matcher(message.getValue());
        assertThat(digits.find()).isTrue();
        String sentCode = digits.group(1);

        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(resetCodeRepo).save(saved.capture());
        PasswordResetCode stored = saved.getValue();
        assertThat(stored.getStaff()).isSameAs(staff);
        assertThat(passwordEncoder.matches(sentCode, stored.getCodeHash())).isTrue();
        assertThat(stored.getFailedAttempts()).isZero();
        assertThat(stored.isSent()).isTrue();
        assertThat(stored.getExpiresAt()).isCloseTo(LocalDateTime.now().plusMinutes(10), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void secondRequestWithinAMinuteIsAConflict() {
        PasswordResetCode recent = pendingCode(0, LocalDateTime.now().plusMinutes(10));
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.findByStaff_Id(staff.getId())).thenReturn(Optional.of(recent));

        assertThatThrownBy(() -> service.requestResetCode(STAFF_ID)).isInstanceOf(ConflictException.class);
        verify(resetCodeRepo, never()).save(any());
        verifyNoInteractions(smsService);
    }

    // The code is kept (not sent) so the staff member can ask again after the cooldown.
    @Test
    void failedSmsIsAServerErrorAndTheCodeIsMarkedNotSent() {
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.findByStaff_Id(staff.getId())).thenReturn(Optional.empty());
        when(smsService.sendSms(anyString(), anyString())).thenReturn(SmsResponse.builder().success(false).build());

        assertThatThrownBy(() -> service.requestResetCode(STAFF_ID)).isInstanceOf(InternalServerError.class);

        ArgumentCaptor<PasswordResetCode> saved = ArgumentCaptor.forClass(PasswordResetCode.class);
        verify(resetCodeRepo).save(saved.capture());
        assertThat(saved.getValue().isSent()).isFalse();
    }

    @Test
    void approvedSmsWording() {
        assertThat(PasswordResetService.resetMessage("482913"))
                .isEqualTo("Your password reset code is 482913. It expires in 10 minutes. Do not share it with anyone.");
    }

    // --- step 2: confirm the code ---

    @Test
    void correctCodeReturnsAResetTokenAndConsumesTheCode() {
        PasswordResetCode pending = pendingCode(0, LocalDateTime.now().plusMinutes(5));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.lockByStaffId(staff.getId())).thenReturn(Optional.of(pending));
        when(jwtUtility.generateResetToken(STAFF_ID)).thenReturn(RESET_TOKEN);

        assertThat(service.confirmResetCode(STAFF_ID, CODE)).isEqualTo(RESET_TOKEN);
        verify(resetCodeRepo).delete(pending);
        verify(staffRepo, never()).save(any());
    }

    @Test
    void wrongCodeCountsTheAttempt() {
        PasswordResetCode pending = pendingCode(0, LocalDateTime.now().plusMinutes(5));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.lockByStaffId(staff.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirmResetCode(STAFF_ID, "000000"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordResetService.INVALID_CODE_MESSAGE);

        assertThat(pending.getFailedAttempts()).isEqualTo(1);
        verify(resetCodeRepo).save(pending);
        verifyNoInteractions(jwtUtility);
    }

    @Test
    void thirdWrongAttemptInvalidatesTheCode() {
        PasswordResetCode pending = pendingCode(2, LocalDateTime.now().plusMinutes(5));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.lockByStaffId(staff.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirmResetCode(STAFF_ID, "000000")).isInstanceOf(BadRequestException.class);

        verify(resetCodeRepo).delete(pending);
        verifyNoInteractions(jwtUtility);
    }

    @Test
    void expiredCodeIsRejectedEvenIfCorrect() {
        PasswordResetCode expired = pendingCode(0, LocalDateTime.now().minusSeconds(1));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.lockByStaffId(staff.getId())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.confirmResetCode(STAFF_ID, CODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordResetService.INVALID_CODE_MESSAGE);
        verify(resetCodeRepo).delete(expired);
        verifyNoInteractions(jwtUtility);
    }

    @Test
    void inactiveStaffCannotConfirmEvenWithAValidCode() {
        staff.setStaffStatus(StaffStatus.INACTIVE);
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.confirmResetCode(STAFF_ID, CODE))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(PasswordResetService.INVALID_CODE_MESSAGE);
        verifyNoInteractions(resetCodeRepo);
    }

    // --- step 3: new password ---

    private NewPassword newPassword(String password) {
        return NewPassword.builder().newPassword(password).resetToken(RESET_TOKEN).build();
    }

    @Test
    void validResetTokenSetsTheNewPassword() {
        when(jwtUtility.isResetToken(RESET_TOKEN)).thenReturn(true);
        when(jwtUtility.extractUsername(RESET_TOKEN)).thenReturn(STAFF_ID);
        when(staffService.getStaffDetails(STAFF_ID)).thenReturn(staff);

        service.saveNewPassword(newPassword("new-password-123"));

        assertThat(passwordEncoder.matches("new-password-123", staff.getPassword())).isTrue();
        verify(staffRepo).save(staff);
    }

    @Test
    void tokenThatIsNotAResetTokenIsRejected() {
        when(jwtUtility.isResetToken(RESET_TOKEN)).thenReturn(false);

        assertThatThrownBy(() -> service.saveNewPassword(newPassword("new-password-123")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid reset token");
        verify(staffRepo, never()).save(any());
    }

    @Test
    void inactiveStaffCannotSetANewPassword() {
        staff.setStaffStatus(StaffStatus.INACTIVE);
        when(jwtUtility.isResetToken(RESET_TOKEN)).thenReturn(true);
        when(jwtUtility.extractUsername(RESET_TOKEN)).thenReturn(STAFF_ID);
        when(staffService.getStaffDetails(STAFF_ID)).thenReturn(staff);

        assertThatThrownBy(() -> service.saveNewPassword(newPassword("new-password-123")))
                .isInstanceOf(UnauthorizedException.class);
        verify(staffRepo, never()).save(any());
    }

    @Test
    void currentPasswordCantBeReused() {
        when(jwtUtility.isResetToken(RESET_TOKEN)).thenReturn(true);
        when(jwtUtility.extractUsername(RESET_TOKEN)).thenReturn(STAFF_ID);
        when(staffService.getStaffDetails(STAFF_ID)).thenReturn(staff);

        assertThatThrownBy(() -> service.saveNewPassword(newPassword("old-password-1")))
                .isInstanceOf(ConflictException.class);
        verify(staffRepo, never()).save(any());
    }
}
