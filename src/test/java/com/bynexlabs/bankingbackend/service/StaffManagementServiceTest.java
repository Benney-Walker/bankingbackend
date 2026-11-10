package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.DTO.staff.StaffUpdateRequest;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.Utility.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Administrator staff management (roadmap 1.5a): list, view and profile updates. */
@ExtendWith(MockitoExtension.class)
class StaffManagementServiceTest {

    private static final String STAFF_ID = "900105";

    @Mock
    private StaffRepo staffRepo;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StaffRolesRepo staffRolesRepo;

    @Mock
    private IdGenerator idGenerator;

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffRepo, staffRolesRepo, idGenerator, new BCryptPasswordEncoder(), auditLogService);
    }

    private Staff existingStaff() {
        Staff staff = Staff.builder()
                .staffId(STAFF_ID)
                .firstName("Kofi")
                .middleName(" ")
                .lastName("Owusu")
                .dateOfBirth(LocalDate.of(1988, 2, 20))
                .phoneNumber("0201234567")
                .email("kofi.owusu@example.com")
                .password("$2a$10$existing-hash")
                .residentialAddress("Takoradi")
                .staffStatus(StaffStatus.ACTIVE)
                .createdAt(LocalDateTime.of(2026, 1, 5, 9, 0))
                .updatedAt(LocalDateTime.of(2026, 1, 5, 9, 0))
                .build();
        StaffRoles agent = new StaffRoles();
        agent.setStaff(staff);
        agent.setRole(Roles.AGENT);
        staff.setRoles(new ArrayList<>(List.of(agent)));
        return staff;
    }

    private StaffUpdateRequest update() {
        return StaffUpdateRequest.builder()
                .firstName("Kofi")
                .lastName("Owusu-Ansah")
                .dateOfBirth("1988-02-20")
                .phoneNumber("0209876543")
                .email("kofi.ansah@example.com")
                .residentialAddress("Tarkwa")
                .build();
    }

    @Test
    void listRejectsPageSizeAboveLimit() {
        assertThatThrownBy(() -> staffService.listStaff(0, StaffService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(staffRepo);
    }

    @Test
    void listReturnsStaffSortedByStaffIdWithPageMetadata() {
        when(staffRepo.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(existingStaff())));

        PagedModel<StaffResponse> result = staffService.listStaff(0, 20);

        assertThat(result.getContent()).extracting(StaffResponse::getStaffId).containsExactly(STAFF_ID);
        assertThat(result.getContent().get(0).getRoles()).containsExactly(Roles.AGENT);
        assertThat(result.getContent().get(0).getMiddleName()).isNull();
        assertThat(result.getMetadata().totalElements()).isEqualTo(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(staffRepo).findAll(pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        assertThat(pageable.getValue().getSort().getOrderFor("staffId")).isNotNull();
    }

    @Test
    void getUnknownStaffIsNotFound() {
        when(staffRepo.findByStaffId("900999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffService.getStaff("900999"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Staff not found");
    }

    @Test
    void updateRejectsEmailBelongingToAnotherStaffMember() {
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(existingStaff()));
        when(staffRepo.existsByEmailAndStaffIdNot("kofi.ansah@example.com", STAFF_ID)).thenReturn(true);

        assertThatThrownBy(() -> staffService.updateStaffProfile(STAFF_ID, update()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already exist");
        verify(staffRepo, never()).save(any());
    }

    @Test
    void updateRejectsInvalidPhoneNumber() {
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(existingStaff()));
        StaffUpdateRequest request = update();
        request.setPhoneNumber("209876543");

        assertThatThrownBy(() -> staffService.updateStaffProfile(STAFF_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid phone number");
        verify(staffRepo, never()).save(any());
    }

    @Test
    void updateChangesProfileButNotPasswordStatusOrRoles() {
        Staff staff = existingStaff();
        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(staff));
        when(staffRepo.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffResponse response = staffService.updateStaffProfile(STAFF_ID, update());

        assertThat(response.getLastName()).isEqualTo("Owusu-Ansah");
        assertThat(response.getPhoneNumber()).isEqualTo("0209876543");
        assertThat(response.getEmail()).isEqualTo("kofi.ansah@example.com");
        assertThat(response.getResidentialAddress()).isEqualTo("Tarkwa");
        assertThat(response.getStatus()).isEqualTo(StaffStatus.ACTIVE);
        assertThat(response.getRoles()).containsExactly(Roles.AGENT);
        assertThat(staff.getPassword()).isEqualTo("$2a$10$existing-hash");
        assertThat(staff.getUpdatedAt()).isAfter(LocalDateTime.of(2026, 1, 5, 9, 0));
        verify(auditLogService).record(eq(AuditAction.STAFF_PROFILE_UPDATED), eq(AuditTargetType.STAFF), eq(STAFF_ID),
                argThat((String details) -> details.startsWith("Changed: ") && details.contains("phoneNumber")
                        && !details.contains("0209876543")));
    }

    @Test
    void invalidDateOfBirthIsBadRequestOnAddAndUpdate() {
        StaffDetails details = StaffDetails.builder()
                .firstName("Ama").lastName("Mensah").dateOfBirth("20/02/1988")
                .phoneNumber("0241234567").email("ama@example.com").password("pw")
                .residentialAddress("Tarkwa").roles(List.of("AGENT"))
                .build();
        assertThatThrownBy(() -> staffService.addNewStaff(details))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid date of birth; expected yyyy-MM-dd");

        when(staffRepo.findByStaffId(STAFF_ID)).thenReturn(Optional.of(existingStaff()));
        StaffUpdateRequest request = update();
        request.setDateOfBirth("1988-13-40");
        assertThatThrownBy(() -> staffService.updateStaffProfile(STAFF_ID, request))
                .isInstanceOf(BadRequestException.class);
    }
}
