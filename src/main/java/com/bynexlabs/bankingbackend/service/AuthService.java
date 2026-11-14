package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Login and token refresh. Collectors are staff accounts with only the COLLECTOR role (owner
 * decision 2026-11-14), but they sign in through their own endpoints: the collector app uses
 * collector-login / refresh-collector-token, which only accept collectors, and the desktop app
 * uses staff-login / refresh-staff-token, which refuse them.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffService staffService;

    private final JwtUtility jwtUtility;

    private final AuthenticationManager authenticationManager;

    public ResponseEntity<?> staffAuthentication(LoginRequest loginRequest) {
        Staff staff = authenticate(loginRequest);
        if (isCollector(staff)) {
            throw new ForbiddenException("Collectors sign in through the collector app");
        }
        return ResponseEntity.ok(issueTokens(staff));
    }

    public LoginResponse collectorAuthentication(LoginRequest loginRequest) {
        Staff staff = authenticate(loginRequest);
        if (!isCollector(staff)) {
            throw new ForbiddenException("Only collector accounts can sign in here");
        }
        return issueTokens(staff);
    }

    public String refreshStaffAccessToken(RefreshRequest refreshRequest) {
        Staff staff = activeStaffForRefresh(refreshRequest);
        // A collector's refresh token can't be swapped for a desktop session.
        if (isCollector(staff)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        return jwtUtility.generateAccessToken(staff.getStaffId(), roleNames(staff));
    }

    public String refreshCollectorAccessToken(RefreshRequest refreshRequest) {
        Staff staff = activeStaffForRefresh(refreshRequest);
        if (!isCollector(staff)) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        return jwtUtility.generateAccessToken(staff.getStaffId(), roleNames(staff));
    }

    // -----------------HELPERS----------------

    /**
     * Checks the staff ID and password (CustomUserDetailsService reads the staffs table, which
     * holds collectors too). Wrong credentials and inactive accounts fail here alike.
     */
    private Staff authenticate(LoginRequest loginRequest) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getStaffId(), loginRequest.getPassword()));
        return staffService.getStaffDetails(loginRequest.getStaffId());
    }

    private LoginResponse issueTokens(Staff staff) {
        List<String> roles = roleNames(staff);
        return LoginResponse.builder()
                .staffName(staff.getFirstName().toUpperCase() + " " + staff.getLastName().toUpperCase())
                .roles(roles)
                .authToken(jwtUtility.generateAccessToken(staff.getStaffId(), roles))
                .refreshToken(jwtUtility.generateRefreshToken(staff.getStaffId()))
                .build();
    }

    private Staff activeStaffForRefresh(RefreshRequest refreshRequest) {
        String refreshToken = refreshRequest.getRefreshToken();
        if (!jwtUtility.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }
        Staff staff = staffService.getStaffDetails(jwtUtility.extractUsername(refreshToken));
        // An inactive staff member must not be able to mint new access tokens.
        if (staff == null || staff.getStaffStatus() != StaffStatus.ACTIVE) {
            throw new UnauthorizedException("Invalid or expired refresh token");
        }
        return staff;
    }

    private static boolean isCollector(Staff staff) {
        return staff.getRoles() != null && staff.getRoles().stream().anyMatch(r -> r.getRole() == Roles.COLLECTOR);
    }

    private static List<String> roleNames(Staff staff) {
        return staff.getRoles().stream().map(role -> role.getRole().name()).toList();
    }
}
