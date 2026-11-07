package com.bynexlabs.bankingbackend.DTO.staff;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StaffDetails {

    private String staffId;

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

    // Same rule as password reset: at least 8 characters (owner, 2026-11-07); at most 72,
    // BCrypt's input limit.
    @NotBlank(message = "Password can't be empty")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    @NotBlank(message = "Address can't be empty")
    private String residentialAddress;

    @NotEmpty(message = "At least one role must be selected")
    private List<String> roles;
}
