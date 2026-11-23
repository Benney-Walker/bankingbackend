package com.bynexlabs.bankingbackend.DTO.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    /**
     * The phone's own device ID, read by the collector app (owner decision 2026-11-23, revised
     * the same day from an app install ID): required at collector-login,
     * ignored at staff-login.
     */
    @Size(max = 100, message = "deviceId is at most 100 characters")
    private String deviceId;
}
