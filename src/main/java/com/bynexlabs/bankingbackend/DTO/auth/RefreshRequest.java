package com.bynexlabs.bankingbackend.DTO.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
