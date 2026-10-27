package com.bynexlabs.bankingbackend.DTO.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {
    @NotBlank(message = "Staff Id cannot be null")
    private String staffId;

    @NotBlank(message = "password cannot be null")
    private String password;
}
