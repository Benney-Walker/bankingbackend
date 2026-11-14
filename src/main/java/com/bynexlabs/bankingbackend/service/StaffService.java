package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.DTO.staff.StaffRolesRequest;
import com.bynexlabs.bankingbackend.DTO.staff.StaffUpdateRequest;
import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRolesRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.Utility.PhoneNumberRules;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepo staffRepo;

    private final StaffRolesRepo staffRolesRepo;

    private final IdGenerator idGenerator;

    private final BCryptPasswordEncoder bCryptPasswordEncoder;

    private final AuditLogService auditLogService;

    private final BranchRepo branchRepo;

    // Upper bound on the staff list page size, so one request can't pull the whole table.
    static final int MAX_PAGE_SIZE = 100;

    public Staff getStaffDetails(String staffId){
        return staffRepo.findByStaffId(staffId).orElse(null);
    }

    // Didn't apply pagination here. It's a deliberate action
    // Will be applied later
    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Staff activeStaff) {
        List<Roles> roles = activeStaff.getRoles().stream().map(
                StaffRoles::getRole
        ).toList();

        if (roles.contains(Roles.AGENT)) {
            List<Staff> staffList = staffRepo.findByBranch_Id(activeStaff.getBranch().getId());
            return staffList.stream().map(StaffResponse::from).collect(Collectors.toList());
        }

        return staffRepo.findAll().stream().map(StaffResponse::from).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StaffResponse getStaff(String staffId) {
        return StaffResponse.from(findStaffOrThrow(staffId));
    }

    @Transactional
    public StaffResponse updateStaffProfile(String staffId, StaffUpdateRequest request) {
        Staff staff = findStaffOrThrow(staffId);
        LocalDate dob = parseDateOfBirth(request.getDateOfBirth());
        validatePhoneNumber(request.getPhoneNumber());

        // Same duplicate rules as add-staff, ignoring the staff member being edited.
        if (staffRepo.existsByFirstNameAndLastNameAndDateOfBirthAndStaffIdNot(
                request.getFirstName(), request.getLastName(), dob, staffId)) {
            throw new ConflictException("Staff already exist");
        }
        if (staffRepo.existsByPhoneNumberAndStaffIdNot(request.getPhoneNumber(), staffId)) {
            throw new ConflictException("Phone number already exist");
        }
        if (staffRepo.existsByEmailAndStaffIdNot(request.getEmail(), staffId)) {
            throw new ConflictException("Email already exist");
        }

        // Field names only: the audit log records what changed, not the personal details themselves.
        String newMiddleName = request.getMiddleName() == null ? " " : request.getMiddleName();
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(staff.getFirstName(), request.getFirstName())) changed.add("firstName");
        if (!Objects.equals(staff.getMiddleName(), newMiddleName)) changed.add("middleName");
        if (!Objects.equals(staff.getLastName(), request.getLastName())) changed.add("lastName");
        if (!Objects.equals(staff.getDateOfBirth(), dob)) changed.add("dateOfBirth");
        if (!Objects.equals(staff.getPhoneNumber(), request.getPhoneNumber())) changed.add("phoneNumber");
        if (!Objects.equals(staff.getEmail(), request.getEmail())) changed.add("email");
        if (!Objects.equals(staff.getResidentialAddress(), request.getResidentialAddress())) changed.add("residentialAddress");

        staff.setFirstName(request.getFirstName());
        staff.setMiddleName(newMiddleName);
        staff.setLastName(request.getLastName());
        staff.setDateOfBirth(dob);
        staff.setPhoneNumber(request.getPhoneNumber());
        staff.setEmail(request.getEmail());
        staff.setResidentialAddress(request.getResidentialAddress());
        staff.setUpdatedAt(LocalDateTime.now());

        Staff saved = staffRepo.save(staff);
        if (!changed.isEmpty()) {
            auditLogService.record(AuditAction.STAFF_PROFILE_UPDATED, AuditTargetType.STAFF, staffId,
                    "Changed: " + String.join(", ", changed));
        }
        return StaffResponse.from(saved);
    }

    /**
     * Replaces the staff member's roles with the requested set. Only the difference is written,
     * so unchanged roles keep their rows. JwtAuthenticationFilter reads roles from the database,
     * so the change applies to the staff member's next request.
     */
    @Transactional
    public StaffResponse updateStaffRoles(String staffId, StaffRolesRequest request, String actingStaffId) {
        Staff staff = findStaffOrThrow(staffId);
        Set<Roles> requested = EnumSet.noneOf(Roles.class);
        requested.addAll(parseRoles(request.getRoles()));

        List<StaffRoles> currentRoles = staff.getRoles() == null ? new ArrayList<>() : staff.getRoles();
        Set<Roles> before = EnumSet.noneOf(Roles.class);
        currentRoles.stream().map(StaffRoles::getRole).forEach(before::add);
        boolean losesAdministrator = currentRoles.stream().anyMatch(r -> r.getRole() == Roles.ADMINISTRATOR)
                && !requested.contains(Roles.ADMINISTRATOR);
        if (losesAdministrator) {
            if (staffId.equals(actingStaffId)) {
                throw new ConflictException("You cannot remove your own ADMINISTRATOR role");
            }
            if (staff.getStaffStatus() == StaffStatus.ACTIVE) {
                requireAnotherActiveAdministrator(staffId);
            }
        }

        List<StaffRoles> removed = currentRoles.stream()
                .filter(r -> !requested.contains(r.getRole()))
                .toList();
        Set<Roles> kept = EnumSet.noneOf(Roles.class);
        currentRoles.stream().map(StaffRoles::getRole).filter(requested::contains).forEach(kept::add);
        List<Roles> added = requested.stream().filter(role -> !kept.contains(role)).toList();

        staffRolesRepo.deleteAll(removed);
        currentRoles.removeAll(removed);
        List<StaffRoles> addedRows = buildStaffRoles(staff, added);
        staffRolesRepo.saveAll(addedRows);
        currentRoles.addAll(addedRows);
        staff.setRoles(currentRoles);

        staff.setUpdatedAt(LocalDateTime.now());
        staffRepo.save(staff);
        if (!before.equals(requested)) {
            auditLogService.record(AuditAction.STAFF_ROLES_CHANGED, AuditTargetType.STAFF, staffId,
                    "Roles " + before + " -> " + requested);
        }
        return StaffResponse.from(staff);
    }

    /** Takes effect immediately: JwtAuthenticationFilter rejects inactive staff on every request. */
    @Transactional
    public StaffResponse deactivateStaff(String staffId, String actingStaffId) {
        Staff staff = findStaffOrThrow(staffId);
        if (staffId.equals(actingStaffId)) {
            throw new ConflictException("You cannot deactivate your own account");
        }
        if (staff.getStaffStatus() == StaffStatus.INACTIVE) {
            return StaffResponse.from(staff);
        }
        boolean isAdministrator = staff.getRoles() != null
                && staff.getRoles().stream().anyMatch(r -> r.getRole() == Roles.ADMINISTRATOR);
        if (isAdministrator) {
            requireAnotherActiveAdministrator(staffId);
        }

        staff.setStaffStatus(StaffStatus.INACTIVE);
        staff.setUpdatedAt(LocalDateTime.now());
        staffRepo.save(staff);
        auditLogService.record(AuditAction.STAFF_DEACTIVATED, AuditTargetType.STAFF, staffId, null);
        return StaffResponse.from(staff);
    }

    @Transactional
    public StaffResponse reactivateStaff(String staffId) {
        Staff staff = findStaffOrThrow(staffId);
        if (staff.getStaffStatus() != StaffStatus.ACTIVE) {
            staff.setStaffStatus(StaffStatus.ACTIVE);
            staff.setUpdatedAt(LocalDateTime.now());
            staffRepo.save(staff);
            auditLogService.record(AuditAction.STAFF_REACTIVATED, AuditTargetType.STAFF, staffId, null);
        }
        return StaffResponse.from(staff);
    }

    // Owner decision: the credit union must never be left without an active administrator,
    // otherwise nobody could manage staff any more.
    private void requireAnotherActiveAdministrator(String staffId) {
        long others = staffRolesRepo.countOtherStaffWithRoleAndStatus(Roles.ADMINISTRATOR, StaffStatus.ACTIVE, staffId);
        if (others == 0) {
            throw new ConflictException("At least one active ADMINISTRATOR is required");
        }
    }

    @Transactional
    public ResponseEntity<?> addNewStaff(StaffDetails staffDetails){
        // Validate the input before touching the database or consuming a staff ID.
        LocalDate dob = parseDateOfBirth(staffDetails.getDateOfBirth());
        validatePhoneNumber(staffDetails.getPhoneNumber());
        List<Roles> roles = parseRoles(staffDetails.getRoles());

        if (staffRepo.existsByFirstNameAndLastNameAndDateOfBirth(
                        staffDetails.getFirstName(), staffDetails.getLastName(), dob)) {
            throw new ConflictException("Staff already exist");
        }

        if (staffRepo.existsByPhoneNumber(staffDetails.getPhoneNumber())) {
            throw new ConflictException("Phone number already exist");
        }

        if (staffRepo.existsByEmail(staffDetails.getEmail())) {
            throw new ConflictException("Email already exist");
        }

        Branch branch = branchRepo.findById(staffDetails.getBranchId())
                .orElseThrow(()-> new ConflictException("Branch not found"));

        String newStaffId = idGenerator.generateId(Id_Type.STAFF_ID);
        String hashedPassword = bCryptPasswordEncoder.encode(staffDetails.getPassword());

        Staff staff = Staff.builder()
                .staffId(newStaffId)
                .firstName(staffDetails.getFirstName())
                .middleName(staffDetails.getMiddleName() == null ? " " :staffDetails.getMiddleName())
                .lastName(staffDetails.getLastName())
                .dateOfBirth(dob)
                .phoneNumber(staffDetails.getPhoneNumber())
                .email(staffDetails.getEmail())
                .password(hashedPassword)
                .residentialAddress(staffDetails.getResidentialAddress())
                .staffStatus(StaffStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .branch(branch)
                .build();
        staffRepo.save(staff);

        saveStaffRoles(staff, roles);
        auditLogService.record(AuditAction.STAFF_CREATED, AuditTargetType.STAFF, newStaffId, "Roles " + roles);

        //Asynchronous task will be here later
        return ResponseEntity.ok().build();
    }

    // --------------------HELPERS------------------------

    public List<GrantedAuthority> getStaffRoles(String staffId) {
        Staff staff = staffRepo.findByStaffId(staffId).orElse(null);
        if (staff == null || staff.getStaffStatus() != StaffStatus.ACTIVE) {
            throw new UnauthorizedException("Staff account inactive or not found");
        }

        return staff.getRoles().stream().map(
                role -> new SimpleGrantedAuthority(role.getRole().name())
        ).collect(Collectors.toUnmodifiableList());
    }

    private List<Roles> parseRoles(List<String> requestedRoles) {
        List<Roles> roles = new ArrayList<>();
        for (String requested : requestedRoles) {
            try {
                roles.add(Roles.valueOf(requested.toUpperCase()));
            } catch (IllegalArgumentException | NullPointerException e) {
                throw new BadRequestException("Invalid role: " + requested
                        + ". Allowed roles: " + Arrays.toString(Roles.values()));
            }
        }
        return roles;
    }

    private void saveStaffRoles(Staff staff, List<Roles> roles) {
        staffRolesRepo.saveAll(buildStaffRoles(staff, roles));
    }

    private List<StaffRoles> buildStaffRoles(Staff staff, List<Roles> roles) {
        List<StaffRoles> staffRoles = new ArrayList<>();
        for (Roles role : roles) {
            StaffRoles newRole = new StaffRoles();
            newRole.setStaff(staff);
            newRole.setRole(role);
            staffRoles.add(newRole);
        }
        return staffRoles;
    }

    private Staff findStaffOrThrow(String staffId) {
        return staffRepo.findByStaffId(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff not found"));
    }

    private LocalDate parseDateOfBirth(String dateOfBirth) {
        try {
            return LocalDate.parse(dateOfBirth, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date of birth; expected yyyy-MM-dd");
        }
    }

    private void validatePhoneNumber(String phoneNumber) {
        PhoneNumberRules.requireLocalFormat(phoneNumber);
    }
}
