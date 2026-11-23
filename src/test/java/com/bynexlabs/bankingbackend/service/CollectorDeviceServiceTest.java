package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.SessionEndReason;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorDeviceServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 18, 0);

    @Mock
    private StaffRepo staffRepo;
    @Mock
    private SessionService sessionService;
    @Mock
    private AuditLogService auditLogService;

    private CollectorDeviceService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch takoradi = Branch.builder().id(UUID.randomUUID()).name("Takoradi").build();
    private final Staff collector = collector();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900105").branch(tarkwa).build();
    private final Staff otherAgent = Staff.builder().id(UUID.randomUUID()).staffId("900106").branch(takoradi).build();

    @BeforeEach
    void setUp() {
        service = new CollectorDeviceService(staffRepo, sessionService, auditLogService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
    }

    private Staff collector() {
        Staff staff = Staff.builder().id(UUID.randomUUID()).staffId("900110").branch(tarkwa).build();
        StaffRoles row = new StaffRoles();
        row.setStaff(staff);
        row.setRole(Roles.COLLECTOR);
        staff.setRoles(List.of(row));
        return staff;
    }

    private void registered(String deviceId) {
        ReflectionTestUtils.setField(collector, "registeredDeviceId", deviceId);
        when(staffRepo.lockById(collector.getId())).thenReturn(Optional.of(collector));
    }

    // --- sign-in ------------------------------------------------------------------------------

    @Test
    void firstSignInRegistersThePhone() {
        registered(null);

        service.requireRegisteredDevice(collector, " phone-1 ");

        verify(staffRepo).registerDevice(collector.getId(), "phone-1", NOW);
        verify(auditLogService).record(AuditAction.COLLECTOR_DEVICE_REGISTERED, AuditTargetType.STAFF, "900110", null);
    }

    @Test
    void theRegisteredPhoneSignsIn() {
        registered("phone-1");

        service.requireRegisteredDevice(collector, "phone-1");

        verify(staffRepo, never()).registerDevice(any(), any(), any());
    }

    @Test
    void anotherPhoneIsRefused() {
        registered("phone-1");

        assertThatThrownBy(() -> service.requireRegisteredDevice(collector, "phone-2"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("registered to another phone");
    }

    @Test
    void signInWithoutAPhoneIdIsRejected() {
        assertThatThrownBy(() -> service.requireRegisteredDevice(collector, " "))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(staffRepo);
    }

    // --- release ------------------------------------------------------------------------------

    @Test
    void branchAgentReleasesThePhoneAndTheCollectorIsSignedOut() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));
        when(staffRepo.releaseDevice(collector.getId())).thenReturn(1);

        service.release("900110", agent, false);

        verify(sessionService).endAllSessions(collector.getId(), SessionEndReason.DEVICE_RELEASED);
        verify(auditLogService).record(AuditAction.COLLECTOR_DEVICE_RELEASED, AuditTargetType.STAFF, "900110",
                "Released by 900105");
    }

    @Test
    void agentOfAnotherBranchCantRelease() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));

        assertThatThrownBy(() -> service.release("900110", otherAgent, false))
                .isInstanceOf(ForbiddenException.class);
        verify(staffRepo, never()).releaseDevice(any());
        verifyNoInteractions(sessionService);
    }

    @Test
    void administratorReleasesAnyBranch() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));
        when(staffRepo.releaseDevice(collector.getId())).thenReturn(1);

        service.release("900110", otherAgent, true);

        verify(sessionService).endAllSessions(collector.getId(), SessionEndReason.DEVICE_RELEASED);
    }

    @Test
    void nothingToReleaseIsAConflict() {
        when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));
        when(staffRepo.releaseDevice(collector.getId())).thenReturn(0);

        assertThatThrownBy(() -> service.release("900110", agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This collector has no registered phone");
        verifyNoInteractions(sessionService);
    }

    @Test
    void unknownCollectorIsNotFound() {
        when(staffRepo.findByStaffId("900199")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.release("900199", agent, true)).isInstanceOf(ResourceNotFoundException.class);
    }
}
