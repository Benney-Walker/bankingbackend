package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.AuditLogRepo;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.models.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

    // --- reading and retention (owner decisions 2026-11-24) ---

    @Test
    void searchPagesNewestFirst() {
        AuditLog entry = AuditLog.builder().id(9L).occurredAt(NOW).actorStaffId("900001").action(AuditAction.LOAN_APPROVED)
                .targetType(AuditTargetType.LOAN_APPLICATION).targetId("app-1").details("Approved").build();
        when(auditLogRepo.findAll(org.mockito.ArgumentMatchers.<Specification<AuditLog>>any(), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        var page = auditLogService.search(null, null, "900001", AuditAction.LOAN_APPROVED, null, null, 0, 20);

        assertThat(page.getContent()).singleElement().satisfies(e -> {
            assertThat(e.id()).isEqualTo(9L);
            assertThat(e.action()).isEqualTo(AuditAction.LOAN_APPROVED);
        });
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepo).findAll(org.mockito.ArgumentMatchers.<Specification<AuditLog>>any(), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("occurredAt").isDescending()).isTrue();
    }

    @Test
    void searchRejectsABadRangeOrPage() {
        assertThatThrownBy(() -> auditLogService.search(NOW, NOW.minusSeconds(1), null, null, null, null, 0, 20))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> auditLogService.search(null, null, null, null, null, null, 0, 101))
                .isInstanceOf(BadRequestException.class);
    }

    // Kept 7 years: everything before 7 Nov 2019 10:15:30 UTC goes.
    @Test
    void entriesOlderThanSevenYearsAreDeleted() {
        when(auditLogRepo.deleteOlderThan(Instant.parse("2019-11-07T10:15:30Z"))).thenReturn(3);

        assertThat(auditLogService.deleteExpired()).isEqualTo(3);
    }
}
