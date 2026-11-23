package com.bynexlabs.bankingbackend.DTO.staff;

import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Staff record as returned to administrators. Deliberately has no password field. */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StaffResponse {

    private String staffId;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String email;
    private String residentialAddress;
    /** Added 2026-11-23 (owner) for the own-profile endpoint; also on every staff response. */
    private UUID branchId;
    private String branchName;
    private StaffStatus status;
    private List<Roles> roles;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Must be called inside a transaction: roles are loaded lazily. */
    public static StaffResponse from(Staff staff) {
        return StaffResponse.builder()
                .staffId(staff.getStaffId())
                .firstName(staff.getFirstName())
                // add-staff stores a single space when no middle name is given; clients get null.
                .middleName(staff.getMiddleName() == null || staff.getMiddleName().isBlank() ? null : staff.getMiddleName())
                .lastName(staff.getLastName())
                .dateOfBirth(staff.getDateOfBirth())
                .phoneNumber(staff.getPhoneNumber())
                .email(staff.getEmail())
                .residentialAddress(staff.getResidentialAddress())
                .branchId(staff.getBranch() == null ? null : staff.getBranch().getId())
                .branchName(staff.getBranch() == null ? null : staff.getBranch().getName())
                .status(staff.getStaffStatus())
                .roles(staff.getRoles() == null ? List.of() : staff.getRoles().stream().map(role -> role.getRole()).toList())
                .createdAt(staff.getCreatedAt())
                .updatedAt(staff.getUpdatedAt())
                .build();
    }
}
