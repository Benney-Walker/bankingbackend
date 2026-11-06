package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.Id_Type;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    private static final String RAW_PASSWORD = "S3cure-pass!";

    @Mock
    private StaffRepo staffRepo;

    @Mock
    private StaffRolesRepo staffRolesRepo;

    @Mock
    private IdGenerator idGenerator;

    @Captor
    private ArgumentCaptor<List<StaffRoles>> savedRoles;

    // A real encoder, so the test proves the stored password is a BCrypt hash of the input.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffRepo, staffRolesRepo, idGenerator, passwordEncoder);
    }

    private StaffDetails newStaff(List<String> roles) {
        return StaffDetails.builder()
                .firstName("Kofi")
                .lastName("Owusu")
                .dateOfBirth("1988-02-20")
                .phoneNumber("0201234567")
                .email("kofi.owusu@example.com")
                .password(RAW_PASSWORD)
                .residentialAddress("Takoradi")
                .roles(roles)
                .build();
    }

    @Test
    void rejectsStaffWithSameNameAndDateOfBirth() {
        when(staffRepo.existsByFirstNameAndLastNameAndDateOfBirth(
                "Kofi", "Owusu", LocalDate.of(1988, 2, 20))).thenReturn(true);

        assertThatThrownBy(() -> staffService.addNewStaff(newStaff(List.of("AGENT"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Staff already exist");
        verify(staffRepo, never()).save(any());
        verifyNoInteractions(idGenerator, staffRolesRepo);
    }

    @Test
    void rejectsDuplicateEmail() {
        when(staffRepo.existsByEmail("kofi.owusu@example.com")).thenReturn(true);

        assertThatThrownBy(() -> staffService.addNewStaff(newStaff(List.of("AGENT"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email already exist");
        verify(staffRepo, never()).save(any());
    }

    @Test
    void savesActiveStaffWithGeneratedIdHashedPasswordAndRoles() {
        when(idGenerator.generateId(Id_Type.STAFF_ID)).thenReturn("900100");

        ResponseEntity<?> response = staffService.addNewStaff(newStaff(List.of("agent", "Collector")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        ArgumentCaptor<Staff> savedStaff = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepo).save(savedStaff.capture());
        Staff staff = savedStaff.getValue();
        assertThat(staff.getStaffId()).isEqualTo("900100");
        assertThat(staff.getStaffStatus()).isEqualTo(StaffStatus.ACTIVE);
        assertThat(staff.getPassword()).isNotEqualTo(RAW_PASSWORD);
        assertThat(passwordEncoder.matches(RAW_PASSWORD, staff.getPassword())).isTrue();

        verify(staffRolesRepo).saveAll(savedRoles.capture());
        assertThat(savedRoles.getValue())
                .extracting(StaffRoles::getRole)
                .containsExactly(Roles.AGENT, Roles.COLLECTOR);
        assertThat(savedRoles.getValue())
                .allSatisfy(role -> assertThat(role.getStaff()).isSameAs(staff));
    }
}
