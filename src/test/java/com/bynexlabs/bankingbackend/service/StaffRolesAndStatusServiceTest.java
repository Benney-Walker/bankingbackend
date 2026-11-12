package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.DTO.staff.StaffRolesRequest;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Administrator role and status changes (roadmap 1.5b), including the owner's lockout rule:
 * no self-deactivation, no self-demotion, and never zero active administrators.
 */
@ExtendWith(MockitoExtension.class)
class StaffRolesAndStatusServiceTest {

    private static final String ACTING_ADMIN = "900100";
    private static final String TARGET = "900105";

    @Mock
    private StaffRepo staffRepo;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StaffRolesRepo staffRolesRepo;

    @Mock
    private IdGenerator idGenerator;

    @Captor
    private ArgumentCaptor<List<StaffRoles>> rows;

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffRepo, staffRolesRepo, idGenerator, new BCryptPasswordEncoder(), auditLogService);
    }

    private Staff staff(String staffId, StaffStatus status, Roles... roles) {
        Staff staff = Staff.builder()
                .staffId(staffId)
                .firstName("Kofi")
                .lastName("Owusu")
                .staffStatus(status)
                .updatedAt(LocalDateTime.of(2026, 1, 5, 9, 0))
                .build();
        List<StaffRoles> staffRoles = new ArrayList<>();
        for (Roles role : roles) {
            StaffRoles row = new StaffRoles();
            row.setStaff(staff);
            row.setRole(role);
            staffRoles.add(row);
        }
        staff.setRoles(staffRoles);
        return staff;
    }

    private void given(Staff staff) {
        when(staffRepo.findByStaffId(staff.getStaffId())).thenReturn(Optional.of(staff));
    }

    // --- deactivate / reactivate ---

    @Test
    void adminCannotDeactivateThemselves() {
        given(staff(ACTING_ADMIN, StaffStatus.ACTIVE, Roles.ADMINISTRATOR));

        assertThatThrownBy(() -> staffService.deactivateStaff(ACTING_ADMIN, ACTING_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You cannot deactivate your own account");
        verify(staffRepo, never()).save(any());
    }

    @Test
    void cannotDeactivateTheLastActiveAdministrator() {
        given(staff(TARGET, StaffStatus.ACTIVE, Roles.ADMINISTRATOR));
        when(staffRolesRepo.countOtherStaffWithRoleAndStatus(Roles.ADMINISTRATOR, StaffStatus.ACTIVE, TARGET))
                .thenReturn(0L);

        assertThatThrownBy(() -> staffService.deactivateStaff(TARGET, ACTING_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessage("At least one active ADMINISTRATOR is required");
        verify(staffRepo, never()).save(any());
    }

    @Test
    void deactivatesAnotherStaffMember() {
        Staff agent = staff(TARGET, StaffStatus.ACTIVE, Roles.AGENT);
        given(agent);

        StaffResponse response = staffService.deactivateStaff(TARGET, ACTING_ADMIN);

        assertThat(response.getStatus()).isEqualTo(StaffStatus.INACTIVE);
        assertThat(agent.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 5, 9, 0));
        verify(staffRepo).save(agent);
        verify(auditLogService).record(AuditAction.STAFF_DEACTIVATED, AuditTargetType.STAFF, TARGET, null);
    }

    @Test
    void deactivatingInactiveStaffChangesNothing() {
        given(staff(TARGET, StaffStatus.INACTIVE, Roles.COLLECTOR));

        StaffResponse response = staffService.deactivateStaff(TARGET, ACTING_ADMIN);

        assertThat(response.getStatus()).isEqualTo(StaffStatus.INACTIVE);
        verify(staffRepo, never()).save(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void reactivatesInactiveStaff() {
        Staff collector = staff(TARGET, StaffStatus.INACTIVE, Roles.COLLECTOR);
        given(collector);

        StaffResponse response = staffService.reactivateStaff(TARGET);

        assertThat(response.getStatus()).isEqualTo(StaffStatus.ACTIVE);
        verify(staffRepo).save(collector);
        verify(auditLogService).record(AuditAction.STAFF_REACTIVATED, AuditTargetType.STAFF, TARGET, null);
    }

    // --- roles ---

    @Test
    void adminCannotRemoveTheirOwnAdministratorRole() {
        given(staff(ACTING_ADMIN, StaffStatus.ACTIVE, Roles.ADMINISTRATOR, Roles.AGENT));

        assertThatThrownBy(() -> staffService.updateStaffRoles(
                ACTING_ADMIN, new StaffRolesRequest(List.of("AGENT")), ACTING_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You cannot remove your own ADMINISTRATOR role");
        verify(staffRolesRepo, never()).deleteAll(any());
    }

    @Test
    void cannotRemoveAdministratorRoleFromLastActiveAdministrator() {
        given(staff(TARGET, StaffStatus.ACTIVE, Roles.ADMINISTRATOR));
        when(staffRolesRepo.countOtherStaffWithRoleAndStatus(Roles.ADMINISTRATOR, StaffStatus.ACTIVE, TARGET))
                .thenReturn(0L);

        assertThatThrownBy(() -> staffService.updateStaffRoles(
                TARGET, new StaffRolesRequest(List.of("AGENT")), ACTING_ADMIN))
                .isInstanceOf(ConflictException.class)
                .hasMessage("At least one active ADMINISTRATOR is required");
    }

    @Test
    void rejectsUnknownRole() {
        given(staff(TARGET, StaffStatus.ACTIVE, Roles.AGENT));

        assertThatThrownBy(() -> staffService.updateStaffRoles(
                TARGET, new StaffRolesRequest(List.of("MANAGER")), ACTING_ADMIN))
                .isInstanceOf(BadRequestException.class);
        verify(staffRolesRepo, never()).saveAll(any());
    }

    @Test
    void writesOnlyTheDifferenceBetweenOldAndNewRoles() {
        Staff target = staff(TARGET, StaffStatus.ACTIVE, Roles.AGENT, Roles.COLLECTOR);
        StaffRoles collectorRow = target.getRoles().get(1);
        given(target);

        StaffResponse response = staffService.updateStaffRoles(
                TARGET, new StaffRolesRequest(List.of("agent", "ADMINISTRATOR")), ACTING_ADMIN);

        assertThat(response.getRoles()).containsExactlyInAnyOrder(Roles.AGENT, Roles.ADMINISTRATOR);

        verify(staffRolesRepo).deleteAll(rows.capture());
        assertThat(rows.getValue()).containsExactly(collectorRow);

        verify(staffRolesRepo).saveAll(rows.capture());
        assertThat(rows.getValue()).extracting(StaffRoles::getRole).containsExactly(Roles.ADMINISTRATOR);
        assertThat(rows.getValue()).allSatisfy(row -> assertThat(row.getStaff()).isSameAs(target));
    }
}
