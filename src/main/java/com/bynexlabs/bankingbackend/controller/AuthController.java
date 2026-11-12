package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.PasswordResetCodeConfirm;
import com.bynexlabs.bankingbackend.DTO.auth.PasswordResetRequest;
import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.service.AuthService;
import com.bynexlabs.bankingbackend.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    private final PasswordResetService passwordResetService;

    @PostMapping("/v1/login")
    public ResponseEntity<?> staffAuthentication(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.staffAuthentication(loginRequest);
    }
    @PostMapping("/v1/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshAccessToken(refreshRequest));
    }

    @PostMapping("/v1/password-reset/request-code")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestResetCode(request.getStaffId());
        return ResponseEntity.ok(Map.of(
                "message", "A reset code has been sent via SMS."
        ));
    }

    @PostMapping("/v1/password-reset/confirm-code")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetCodeConfirm request) {

        return ResponseEntity.ok(passwordResetService.confirmResetCode(request.getStaffId(), request.getCode()));
    }
}
