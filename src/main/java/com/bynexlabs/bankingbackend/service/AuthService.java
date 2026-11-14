package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Collector;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffService staffService;

    private final JwtUtility jwtUtility;

    private final AuthenticationManager authenticationManager;

    private final CollectorService collectorService;

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

    public LoginResponse collectorAuthentication(LoginRequest loginRequest) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getStaffId(),
                        loginRequest.getPassword()
                )
        );

        Collector collector = collectorService.getCollector(loginRequest.getStaffId());

        List<String> rolesList = Collections.singletonList(collector.getRole().name());

        String accessToken = jwtUtility.generateAccessToken(
                loginRequest.getStaffId(),
                rolesList
        );

        String refreshToken = jwtUtility.generateRefreshToken(
                loginRequest.getStaffId()
        );

        return LoginResponse.builder()
                        .staffName(
                                collector.getFirstName().toUpperCase() + " " + collector.getLastName().toUpperCase())
                        .roles(rolesList)
                        .authToken(accessToken)
                        .refreshToken(refreshToken)
                        .build();
    }

    public String refreshStaffAccessToken(RefreshRequest refreshRequest) {

        verifyRefreshToken(refreshRequest);

        String staffId = jwtUtility.extractUsername(refreshRequest.getRefreshToken());
        Staff staff = staffService.getStaffDetails(staffId);
        // An inactive staff member must not be able to mint new access tokens.
        if (staff == null || staff.getStaffStatus() != StaffStatus.ACTIVE) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }

        List<String> rolesList = staff.getRoles().stream()
                .map(role -> role.getRole().name())
                .toList();

        return jwtUtility.generateAccessToken(staffId, rolesList);
    }

    // -----------------HELPER----------------
    private void verifyRefreshToken(RefreshRequest refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();

        if (!jwtUtility.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }
    }

}
