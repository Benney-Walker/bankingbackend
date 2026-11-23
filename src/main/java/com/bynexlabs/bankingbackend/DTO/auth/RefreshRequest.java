package com.bynexlabs.bankingbackend.DTO.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshRequest {
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;

    /** The collector's phone's device ID: required to refresh a collector session (V42). */
    @Size(max = 100, message = "deviceId is at most 100 characters")
    private String deviceId;

    public RefreshRequest(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
