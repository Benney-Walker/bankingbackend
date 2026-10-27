package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;

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
}
