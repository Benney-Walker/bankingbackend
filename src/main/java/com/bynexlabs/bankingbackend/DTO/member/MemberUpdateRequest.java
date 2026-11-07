package com.bynexlabs.bankingbackend.DTO.member;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The only member details that can change after registration (owner rule, 2026-11-07). Names,
 * date of birth and everything else are locked; there are deliberately no fields for them.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberUpdateRequest {

    @NotBlank(message = "Phone number cannot be empty")
    private String phoneNumber;

    @NotBlank(message = "Address cannot be empty")
    private String residentialAddress;

    @NotBlank(message = "Occupation field cannot be empty")
    private String occupation;
}
