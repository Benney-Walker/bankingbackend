package com.bynexlabs.bankingbackend.DTO.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String staffName;

    private List<String> roles;

    private String institutionName;

    private String authToken;

    private String refreshToken;
}
