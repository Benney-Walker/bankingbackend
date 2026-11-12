package com.bynexlabs.bankingbackend.DTO.staff;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Profile fields an administrator may change on an existing staff member. Staff ID, password,
 * roles and status are changed through their own endpoints.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StaffUpdateRequest {

    @NotBlank(message = "First name cannot be empty")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name cannot be empty")
    private String lastName;

    @NotBlank(message = "Date of birth can't be empty")
    private String dateOfBirth;

    @NotBlank(message = "Phone number can't be empty")
    private String phoneNumber;

    @NotBlank(message = "Email can't be empty")
    private String email;

    @NotBlank(message = "Address can't be empty")
    private String residentialAddress;
}
