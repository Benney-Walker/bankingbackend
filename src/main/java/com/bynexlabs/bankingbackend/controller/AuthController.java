package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.auth.*;
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

    @PostMapping("/v1/staff-login")
    public ResponseEntity<?> staffAuthentication(@Valid @RequestBody LoginRequest loginRequest) {
        return authService.staffAuthentication(loginRequest);
    }

    @PostMapping("/v1/collector-login")
    public ResponseEntity<?> collectorAuthentication(@Valid @RequestBody LoginRequest loginRequest) {

        return ResponseEntity.ok(authService.collectorAuthentication(loginRequest));
    }

    @PostMapping("/v1/refresh-staff-token")
    public ResponseEntity<?> refreshStaffToken(@Valid @RequestBody RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshStaffAccessToken(refreshRequest));
    }

    @PostMapping("/v1/refresh-collector-token")
    public ResponseEntity<?> refreshCollectorToken(@Valid @RequestBody RefreshRequest refreshRequest) {
        return ResponseEntity.ok(authService.refreshCollectorAccessToken(refreshRequest));
    }

    @PostMapping("/v1/password-reset/request-code")
    public ResponseEntity<?> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestResetCode(request.getStaffId());
        return ResponseEntity.ok(Map.of(
                "message", "A reset code has been sent via SMS."
        ));
    }

    @PostMapping("/v1/password-reset/confirm-code")
    public ResponseEntity<?> confirmPasswordResetCode(@Valid @RequestBody PasswordResetCodeConfirm request) {

        return ResponseEntity.ok(passwordResetService.confirmResetCode(request.getStaffId(), request.getCode()));
    }

    @PutMapping("/v1/password-reset/new-password")
    public ResponseEntity<?> saveNewPassword(@Valid @RequestBody NewPassword newPassword) {
        passwordResetService.saveNewPassword(newPassword);
        return ResponseEntity.ok().build();
    }
}
