package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.sms.SmsResponse;
import com.bynexlabs.bankingbackend.Repository.PasswordResetCodeRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
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

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
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

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    private static final String STAFF_ID = "900105";
    private static final String PHONE = "0201234567";
    private static final String CODE = "482913";

    @Mock
    private StaffRepo staffRepo;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private PasswordResetCodeRepo resetCodeRepo;

    @Mock
    private SmsInterface smsService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private PasswordResetService service;

    private Staff staff;

    private JwtUtility jwtUtility;

    @BeforeEach
    void setUp() {
        service = new PasswordResetService(staffRepo, resetCodeRepo, passwordEncoder, smsService, jwtUtility);
        staff = Staff.builder()
                .id(UUID.randomUUID())
                .staffId(STAFF_ID)
                .phoneNumber(PHONE)
                .password("$2a$10$old-hash")
                .staffStatus(StaffStatus.ACTIVE)
                .build();
    }

    private PasswordResetCode pendingCode(int failedAttempts, LocalDateTime expiresAt) {
        return PasswordResetCode.builder()
                .staff(staff)
                .codeHash(passwordEncoder.encode(CODE))
                .failedAttempts(failedAttempts)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // --- request ---

    @Test
    void requestForUnknownStaffDoesNothingVisible() {
        when(staffRepo.findByStaffId("900999")).thenReturn(Optional.empty());

        service.requestResetCode("900999");

        verifyNoInteractions(resetCodeRepo, smsService);
    }

    @Test
    void requestForInactiveStaffDoesNothingVisible() {
        staff.setStaffStatus(StaffStatus.INACTIVE);
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));

        service.requestResetCode(STAFF_ID);

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
        assertThat(stored.getCodeHash()).isNotEqualTo(sentCode);
        assertThat(passwordEncoder.matches(sentCode, stored.getCodeHash())).isTrue();
        assertThat(stored.getFailedAttempts()).isZero();
        assertThat(stored.getExpiresAt()).isCloseTo(LocalDateTime.now().plusMinutes(10), within(5, ChronoUnit.SECONDS));
    }

    @Test
    void newRequestReplacesThePendingCodeAndResetsAttempts() {
        PasswordResetCode existing = pendingCode(2, LocalDateTime.now().plusMinutes(3));
        existing.setCreatedAt(LocalDateTime.now().minusMinutes(7));
        String oldHash = existing.getCodeHash();
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.findByStaff_Id(staff.getId())).thenReturn(Optional.of(existing));
        when(smsService.sendSms(anyString(), anyString())).thenReturn(SmsResponse.builder().success(true).build());

        service.requestResetCode(STAFF_ID);

        verify(resetCodeRepo).save(existing);
        assertThat(existing.getCodeHash()).isNotEqualTo(oldHash);
        assertThat(existing.getFailedAttempts()).isZero();
    }

    @Test
    void secondRequestWithinAMinuteSendsNothing() {
        PasswordResetCode recent = pendingCode(0, LocalDateTime.now().plusMinutes(10));
        recent.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        String hash = recent.getCodeHash();
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.findByStaff_Id(staff.getId())).thenReturn(Optional.of(recent));

        service.requestResetCode(STAFF_ID);

        verify(resetCodeRepo, never()).save(any());
        verifyNoInteractions(smsService);
        assertThat(recent.getCodeHash()).isEqualTo(hash);
    }

    @Test
    void approvedSmsWording() {
        assertThat(PasswordResetService.resetMessage("482913"))
                .isEqualTo("Your password reset code is 482913. It expires in 10 minutes. Do not share it with anyone.");
    }

    @Test
    void failedSmsStillKeepsTheCodeSoTheStaffCanRetry() {
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.findByStaff_Id(staff.getId())).thenReturn(Optional.empty());
        when(smsService.sendSms(anyString(), anyString())).thenReturn(SmsResponse.builder().success(false).build());

        service.requestResetCode(STAFF_ID);

        verify(resetCodeRepo).save(any(PasswordResetCode.class));
    }

    // --- confirm ---

    @Test
    void correctCodeSetsTheNewPasswordAndConsumesTheCode() {
        PasswordResetCode pending = pendingCode(0, LocalDateTime.now().plusMinutes(5));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.lockByStaffId(staff.getId())).thenReturn(Optional.of(pending));

        service.confirmResetCode(STAFF_ID, CODE);

        assertThat(passwordEncoder.matches("new-password-123", staff.getPassword())).isTrue();
        verify(staffRepo).save(staff);
        verify(resetCodeRepo).delete(pending);
        verify(auditLogService).recordAs(STAFF_ID, AuditAction.STAFF_PASSWORD_RESET, AuditTargetType.STAFF,
                STAFF_ID, "Reset with SMS code");
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
        verify(staffRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void thirdWrongAttemptInvalidatesTheCode() {
        PasswordResetCode pending = pendingCode(2, LocalDateTime.now().plusMinutes(5));
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(resetCodeRepo.lockByStaffId(staff.getId())).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> service.confirmResetCode(STAFF_ID, "000000"))
                .isInstanceOf(BadRequestException.class);

        verify(resetCodeRepo).delete(pending);
        verify(staffRepo, never()).save(any());
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
        verify(staffRepo, never()).save(any());
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
}
