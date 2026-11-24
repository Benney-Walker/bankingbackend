package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.StaffSmsEvent;
import java.time.Instant;
import org.springframework.security.core.GrantedAuthority;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.List;

import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.models.Branch;
import java.util.Optional;
import java.util.UUID;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private OutboxService outboxService;

    @Mock
    private SessionService sessionService;

    @Mock
    private StaffRepo staffRepo;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private StaffRolesRepo staffRolesRepo;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private BranchRepo branchRepo;

    private final Branch branch = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();

    @Captor
    private ArgumentCaptor<List<StaffRoles>> savedRoles;

    // A real encoder, so the test proves the stored password is a BCrypt hash.
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private StaffService staffService;

    @BeforeEach
    void setUp() {
        staffService = new StaffService(staffRepo, staffRolesRepo, idGenerator, passwordEncoder, auditLogService, branchRepo, sessionService, outboxService);
        staffService.publicBaseUrl = "https://bank.example/";
    }

    private StaffDetails newStaff(List<String> roles) {
        return StaffDetails.builder()
                .firstName("Kofi")
                .lastName("Owusu")
                .dateOfBirth("1988-02-20")
                .phoneNumber("0201234567")
                .email("kofi.owusu@example.com")
                .residentialAddress("Takoradi")
                .roles(roles)
                .branchId(branch.getId())
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

    @ParameterizedTest
    @ValueSource(strings = {"241234567", "1241234567", "02412345678"})
    void rejectsPhoneNumberThatIsNotTenDigitsStartingWithZero(String phoneNumber) {
        StaffDetails details = newStaff(List.of("AGENT"));
        details.setPhoneNumber(phoneNumber);

        assertThatThrownBy(() -> staffService.addNewStaff(details))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid phone number");
        verifyNoInteractions(staffRepo, idGenerator, staffRolesRepo);
    }

    @Test
    void rejectsUnknownRoleBeforeSavingAnything() {
        assertThatThrownBy(() -> staffService.addNewStaff(newStaff(List.of("AGENT", "MANAGER"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageStartingWith("Invalid role: MANAGER");
        verifyNoInteractions(staffRepo, idGenerator, staffRolesRepo);
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
        when(branchRepo.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(branchRepo.lockById(branch.getId())).thenReturn(Optional.of(branch));

        ResponseEntity<?> response = staffService.addNewStaff(newStaff(List.of("agent", "Administrator")));

        assertThat(response.getStatusCode().value()).isEqualTo(200);

        ArgumentCaptor<Staff> savedStaff = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepo).save(savedStaff.capture());
        Staff staff = savedStaff.getValue();
        assertThat(staff.getStaffId()).isEqualTo("900100");
        assertThat(staff.getStaffStatus()).isEqualTo(StaffStatus.ACTIVE);
        // Owner decision 2026-11-15: a random password nobody knows, stored as a BCrypt hash.
        assertThat(staff.getPassword()).startsWith("$2");

        verify(staffRolesRepo).saveAll(savedRoles.capture());
        assertThat(savedRoles.getValue())
                .extracting(StaffRoles::getRole)
                .containsExactly(Roles.AGENT, Roles.ADMINISTRATOR);
        assertThat(savedRoles.getValue())
                .allSatisfy(role -> assertThat(role.getStaff()).isSameAs(staff));
        assertThat(staff.getBranch()).isSameAs(branch);

        // Owner decision 2026-11-15: the staff ID is texted to the new staff member.
        ArgumentCaptor<StaffSmsEvent> sms = ArgumentCaptor.forClass(StaffSmsEvent.class);
        verify(outboxService).enqueue(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.STAFF_SMS_ROUTING_KEY), sms.capture());
        assertThat(sms.getValue().getStaffId()).isEqualTo("900100");
        assertThat(sms.getValue().getPhoneNumber()).isEqualTo("0201234567");
        // Owner decision 2026-11-24: the SMS links to the server's reset page with the staff ID filled in.
        assertThat(sms.getValue().getMessage()).isEqualTo("Hello Kofi, you've been added as staff at Tarkwa branch. "
                + "Your staff ID is 900100. Set your password at https://bank.example/password-reset?staffId=900100 "
                + "before you sign in.");
    }

    // Each new staff member gets a different random password.
    @Test
    void generatedPasswordsDiffer() {
        when(idGenerator.generateId(Id_Type.STAFF_ID)).thenReturn("900101", "900102");
        when(branchRepo.findById(branch.getId())).thenReturn(Optional.of(branch));

        staffService.addNewStaff(newStaff(List.of("COLLECTOR")));
        StaffDetails second = newStaff(List.of("COLLECTOR"));
        second.setPhoneNumber("0201234568");
        second.setEmail("other@example.com");
        staffService.addNewStaff(second);

        ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepo, org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getPassword()).isNotEqualTo(saved.getAllValues().get(1).getPassword());
    }

    @Test
    void failedValidationQueuesNoSms() {
        assertThatThrownBy(() -> staffService.addNewStaff(newStaff(List.of("AGENT", "COLLECTOR"))))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(outboxService);
    }

    // Owner rule (2026-11-14): collectors are staff with only the COLLECTOR role.
    @Test
    void collectorRoleCantBeCombinedWithAnother() {
        assertThatThrownBy(() -> staffService.addNewStaff(newStaff(List.of("AGENT", "COLLECTOR"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("COLLECTOR can't be combined with other roles");
        verify(staffRepo, never()).save(any());
    }

    // Owner rule (2026-11-14): one active agent per branch.
    @Test
    void secondActiveAgentInABranchIsRejected() {
        when(branchRepo.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(branchRepo.lockById(branch.getId())).thenReturn(Optional.of(branch));
        when(staffRolesRepo.countOtherStaffInBranchWithRoleAndStatus(Roles.AGENT, StaffStatus.ACTIVE, branch.getId(), ""))
                .thenReturn(1L);

        assertThatThrownBy(() -> staffService.addNewStaff(newStaff(List.of("AGENT"))))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Branch Tarkwa already has an active agent");
        verify(staffRepo, never()).save(any());
        verifyNoInteractions(idGenerator);
    }

    // Any number of collectors per branch: no agent check, no branch lock.
    @Test
    void collectorsAreNotLimitedPerBranch() {
        when(idGenerator.generateId(Id_Type.STAFF_ID)).thenReturn("900101");
        when(branchRepo.findById(branch.getId())).thenReturn(Optional.of(branch));

        staffService.addNewStaff(newStaff(List.of("COLLECTOR")));

        verify(staffRepo).save(any(Staff.class));
        verify(branchRepo, never()).lockById(any());
    }

    // --- session cut-off (owner decision 2026-11-15) ---------------------------------------

    private Staff activeAgentWithCutoff(Instant cutoff) {
        Staff staff = Staff.builder().id(UUID.randomUUID()).staffId("900105").staffStatus(StaffStatus.ACTIVE)
                .sessionsValidAfter(cutoff).build();
        StaffRoles row = new StaffRoles();
        row.setStaff(staff);
        row.setRole(Roles.AGENT);
        staff.setRoles(List.of(row));
        when(staffRepo.findByStaffId("900105")).thenReturn(Optional.of(staff));
        return staff;
    }

    @Test
    void accessTokenIssuedBeforeTheSessionsEndedIsRefused() {
        activeAgentWithCutoff(Instant.parse("2026-11-15T16:30:00Z"));

        assertThatThrownBy(() -> staffService.getStaffRoles("900105", Instant.parse("2026-11-15T16:29:59Z")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Session has ended");
    }

    @Test
    void accessTokenIssuedAfterTheCutoffWorks() {
        activeAgentWithCutoff(Instant.parse("2026-11-15T16:30:00Z"));

        assertThat(staffService.getStaffRoles("900105", Instant.parse("2026-11-15T16:30:00Z")))
                .extracting(GrantedAuthority::getAuthority).containsExactly("AGENT");
    }

    @Test
    void noCutoffMeansAnyValidTokenWorks() {
        activeAgentWithCutoff(null);

        assertThat(staffService.getStaffRoles("900105", Instant.parse("2020-01-01T00:00:00Z"))).hasSize(1);
    }
}
