package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 8, 0);

    @Mock private StaffRepo staffRepo;
    @Mock private AuditLogService auditLogService;

    private LoginAttemptService service;

    private final Staff staff = Staff.builder().id(UUID.randomUUID()).staffId("900105").firstName("Esi").lastName("Boateng").build();

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(staffRepo, auditLogService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
    }

    @Test
    void wrongPasswordIsCountedAndAudited() {
        when(staffRepo.findByStaffId("900105")).thenReturn(Optional.of(staff));
        when(staffRepo.recordFailedLogin(staff.getId(), 5, NOW)).thenReturn(1);
        when(staffRepo.findFailedLoginAttempts(staff.getId())).thenReturn(2);

        service.wrongPassword("900105");

        verify(auditLogService).recordAs(null, AuditAction.LOGIN_FAILED, AuditTargetType.STAFF, "900105",
                "Wrong password (2 of 5 in a row)");
        verify(auditLogService, never()).recordAs(any(), eq(AuditAction.STAFF_LOCKED), any(), any(), any());
    }

    // Owner decision 2026-11-24: the 5th wrong password in a row locks the account.
    @Test
    void fifthWrongPasswordLocksTheAccount() {
        when(staffRepo.findByStaffId("900105")).thenReturn(Optional.of(staff));
        when(staffRepo.recordFailedLogin(staff.getId(), 5, NOW)).thenReturn(1);
        when(staffRepo.findFailedLoginAttempts(staff.getId())).thenReturn(5);

        service.wrongPassword("900105");

        verify(auditLogService).recordAs(null, AuditAction.STAFF_LOCKED, AuditTargetType.STAFF, "900105",
                "Locked after 5 wrong passwords in a row");
    }

    // Nothing typed into the staff ID field is stored when it names no account.
    @Test
    void unknownStaffIdIsNotStored() {
        when(staffRepo.findByStaffId("my-password")).thenReturn(Optional.empty());

        service.wrongPassword("my-password");

        verify(staffRepo, never()).recordFailedLogin(any(), anyInt(), any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void successfulSignInIsAudited() {
        service.signedIn(staff, "collector app");

        verify(auditLogService).recordAs("900105", AuditAction.LOGIN_SUCCEEDED, AuditTargetType.STAFF, "900105",
                "Signed in to the collector app");
    }

    @Test
    void administratorUnlocks() {
        staff.setLockedAt(NOW.minusHours(1));
        when(staffRepo.findByStaffId("900105")).thenReturn(Optional.of(staff));
        when(staffRepo.unlock(staff.getId())).thenReturn(1);

        StaffResponse response = service.unlock("900105", "900001");

        assertThat(response.getLockedAt()).isNull();
        verify(auditLogService).recordAs("900001", AuditAction.STAFF_UNLOCKED, AuditTargetType.STAFF, "900105", "Unlocked");
    }

    @Test
    void unlockingAnAccountThatIsntLockedIsRefused() {
        when(staffRepo.findByStaffId("900105")).thenReturn(Optional.of(staff));
        when(staffRepo.unlock(staff.getId())).thenReturn(0);

        assertThatThrownBy(() -> service.unlock("900105", "900001"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This account isn't locked");
    }
}
