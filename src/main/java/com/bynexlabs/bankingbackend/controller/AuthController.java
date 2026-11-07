package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.PasswordResetConfirmRequest;
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

    // POST rather than GET: the refresh token travels in the JSON body, and GET bodies are
    // dropped by some clients and proxies.
    @PostMapping("/v1/refresh-token")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshRequest refreshRequest) {
        return authService.refreshAccessToken(refreshRequest);
    }

    // Public: used from the reset-password page by staff who can't log in. The response is the
    // same whether or not the staff ID exists, so it can't be used to discover staff IDs.
    @PostMapping("/v1/password-reset/request")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.getStaffId());
        return ResponseEntity.ok(Map.of(
                "message", "If the staff ID belongs to an active staff member, a reset code has been sent by SMS."
        ));
    }

    @PostMapping("/v1/password-reset/confirm")
    public ResponseEntity<?> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.getStaffId(), request.getCode(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password has been reset"));
    }
}
