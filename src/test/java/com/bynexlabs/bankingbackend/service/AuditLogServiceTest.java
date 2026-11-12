package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.AuditLogRepo;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.models.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:15:30Z");

    @Mock
    private AuditLogRepo auditLogRepo;

    @Mock
    private AuthenticatedStaffProvider authenticatedStaffProvider;

    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        auditLogService = new AuditLogService(auditLogRepo, authenticatedStaffProvider);
        auditLogService.clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private AuditLog saved() {
        ArgumentCaptor<AuditLog> entry = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepo).save(entry.capture());
        return entry.getValue();
    }

    @Test
    void recordUsesTheStaffMemberMakingTheRequest() {
        when(authenticatedStaffProvider.getStaffId()).thenReturn("900100");

        auditLogService.record(AuditAction.STAFF_DEACTIVATED, AuditTargetType.STAFF, "900105", null);

        AuditLog entry = saved();
        assertThat(entry.getActorStaffId()).isEqualTo("900100");
        assertThat(entry.getAction()).isEqualTo(AuditAction.STAFF_DEACTIVATED);
        assertThat(entry.getTargetType()).isEqualTo(AuditTargetType.STAFF);
        assertThat(entry.getTargetId()).isEqualTo("900105");
        assertThat(entry.getOccurredAt()).isEqualTo(NOW);
        assertThat(entry.getDetails()).isNull();
    }

    @Test
    void recordAsUsesTheNamedStaffMember() {
        auditLogService.recordAs("900105", AuditAction.STAFF_PASSWORD_RESET, AuditTargetType.STAFF, "900105", "Reset with SMS code");

        assertThat(saved().getActorStaffId()).isEqualTo("900105");
        verifyNoInteractions(authenticatedStaffProvider);
    }

    @Test
    void systemActionsHaveNoActorAndUuidTargetsAreStoredAsText() {
        UUID memberId = UUID.randomUUID();

        auditLogService.recordSystem(AuditAction.KYC_AUTO_VERIFIED, AuditTargetType.MEMBER, memberId, "Member and accounts activated");

        AuditLog entry = saved();
        assertThat(entry.getActorStaffId()).isNull();
        assertThat(entry.getTargetId()).isEqualTo(memberId.toString());
    }

    @Test
    void longDetailsAreTruncated() {
        auditLogService.recordSystem(AuditAction.KYC_REJECTED, AuditTargetType.MEMBER, "m", "x".repeat(5000));

        String details = saved().getDetails();
        assertThat(details).hasSize(2000).endsWith("...");
    }

    @Test
    void actionTargetTypeAndTargetIdAreRequired() {
        assertThatThrownBy(() -> auditLogService.recordSystem(null, AuditTargetType.MEMBER, "m", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditLogService.recordSystem(AuditAction.KYC_APPROVED, null, "m", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> auditLogService.recordSystem(AuditAction.KYC_APPROVED, AuditTargetType.MEMBER, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(auditLogRepo);
    }
}
