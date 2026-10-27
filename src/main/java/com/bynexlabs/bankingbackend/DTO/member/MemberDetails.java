package com.bynexlabs.bankingbackend.DTO.member;

import jakarta.persistence.Column;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberDetails {

    @NotBlank(message = "First name cannot be empty")
    private String firstName;

    private String middleName;

    @NotBlank(message = "Last name cannot be empty")
    private String lastName;

    @NotBlank(message = "Date of birth cannot be empty")
    private String dob;

    @NotBlank(message = "Phone number cannot be empty")
    private String phoneNumber;

    @NotBlank(message = "Address cannot be empty")
    private String residentialAddress;

    @NotBlank(message = "Occupation field cannot be empty")
    private String occupation;

    @NotBlank(message = "Account type can't be empty")
    private String accountType;
}
