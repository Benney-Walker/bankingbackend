package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/v1/login")
    public ResponseEntity<?> staffAuthentication(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.staffAuthentication(loginRequest);
    }

    @GetMapping("/v1/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshRequest refreshRequest) {
        return authService.refreshAccessToken(refreshRequest);
    }
}
