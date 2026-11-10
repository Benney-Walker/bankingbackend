package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffService staffService;

    private final JwtUtility jwtUtility;

    private final AuthenticationManager authenticationManager;

    public ResponseEntity<?> staffAuthentication(LoginRequest loginRequest) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getStaffId(),
                        loginRequest.getPassword()
                )
        );

        Staff staff = staffService.getStaffDetails(loginRequest.getStaffId());

        List<String> rolesList = staff.getRoles().stream().map(
                role -> role.getRole().name()).toList();

        String accessToken = jwtUtility.generateAccessToken(
                loginRequest.getStaffId(),
                rolesList
        );

        String refreshToken = jwtUtility.generateRefreshToken(
                loginRequest.getStaffId()
        );

        return ResponseEntity.ok(
                LoginResponse.builder()
                        .staffName(staff.getFirstName().toUpperCase() + " " + staff.getLastName().toUpperCase())
                        .roles(rolesList)
                        .authToken(accessToken)
                        .refreshToken(refreshToken)
                        .build()
        );
    }

    public ResponseEntity<?> refreshAccessToken(RefreshRequest refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();

        if (!jwtUtility.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        String staffId = jwtUtility.extractUsername(refreshToken);
        Staff staff = staffService.getStaffDetails(staffId);
        // An inactive staff member must not be able to mint new access tokens.
        if (staff == null || staff.getStaffStatus() != StaffStatus.ACTIVE) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        List<String> rolesList = staff.getRoles().stream()
                .map(role -> role.getRole().name())
                .toList();

        String newAccessToken = jwtUtility.generateAccessToken(staffId, rolesList);

        return ResponseEntity.ok(Map.of(
                "authToken", newAccessToken
        ));
    }
}
