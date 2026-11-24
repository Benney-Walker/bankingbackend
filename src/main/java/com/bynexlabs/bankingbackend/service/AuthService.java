package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.DTO.auth.TokenResponse;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Predicate;

/**
 * Login and token refresh. Collectors are staff accounts with only the COLLECTOR role (owner
 * decision 2026-11-14), but they sign in through their own endpoints: the collector app uses
 * collector-login / refresh-collector-token, which only accept collectors, and the desktop app
 * uses staff-login / refresh-staff-token, which refuse them.
 *
 * Refresh tokens are kept on the server and swapped on every refresh; logout ends the session
 * (SessionService). A collector whose daily submission was approved today can't sign in again
 * until tomorrow (owner decision 2026-11-15).
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffService staffService;

    private final JwtUtility jwtUtility;

    private final AuthenticationManager authenticationManager;

    private final SessionService sessionService;

    private final CollectorSubmissionService collectorSubmissionService;

    private final CollectorDeviceService collectorDeviceService;

    private final LoginAttemptService loginAttemptService;

    public ResponseEntity<?> staffAuthentication(LoginRequest loginRequest) {
        Staff staff = authenticate(loginRequest);
        if (isCollector(staff)) {
            throw new ForbiddenException("Collectors sign in through the collector app");
        }
        LoginResponse tokens = issueTokens(staff);
        loginAttemptService.signedIn(staff, "desktop app");
        return ResponseEntity.ok(tokens);
    }

    public LoginResponse collectorAuthentication(LoginRequest loginRequest) {
        Staff staff = authenticate(loginRequest);
        if (!isCollector(staff)) {
            throw new ForbiddenException("Only collector accounts can sign in here");
        }
        if (collectorSubmissionService.isClosedForToday(staff.getId())) {
            throw new ForbiddenException("Your collections for today have been approved. You can sign in again tomorrow");
        }
        // Owner decision 2026-11-23: one registered phone per collector.
        collectorDeviceService.requireRegisteredDevice(staff, loginRequest.getDeviceId());
        LoginResponse tokens = issueTokens(staff, loginRequest.getDeviceId());
        loginAttemptService.signedIn(staff, "collector app");
        return tokens;
    }

    /** A collector's refresh token can't be swapped for a desktop session. */
    public TokenResponse refreshStaffAccessToken(RefreshRequest refreshRequest) {
        return refresh(refreshRequest, roles -> !roles.contains(Roles.COLLECTOR));
    }

    public TokenResponse refreshCollectorAccessToken(RefreshRequest refreshRequest) {
        return refresh(refreshRequest, roles -> roles.contains(Roles.COLLECTOR));
    }

    /** Signs out this device. Always succeeds, so it reveals nothing about the token. */
    public void logout(RefreshRequest refreshRequest) {
        sessionService.endSession(refreshRequest.getRefreshToken());
    }

    // -----------------HELPERS----------------

    /**
     * Checks the staff ID and password (CustomUserDetailsService reads the staffs table, which
     * holds collectors too). Wrong credentials and inactive accounts fail here alike. Owner
     * decisions 2026-11-24: wrong passwords are counted (5 in a row lock the account until an
     * administrator unlocks it) and every attempt is audited.
     */
    private Staff authenticate(LoginRequest loginRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getStaffId(), loginRequest.getPassword()));
        } catch (BadCredentialsException e) {
            loginAttemptService.wrongPassword(loginRequest.getStaffId());
            throw e;
        } catch (LockedException e) {
            loginAttemptService.refused(loginRequest.getStaffId(), "Account locked");
            throw e;
        } catch (DisabledException e) {
            loginAttemptService.refused(loginRequest.getStaffId(), "Account not active");
            throw e;
        }
        Staff staff = staffService.getStaffDetails(loginRequest.getStaffId());
        loginAttemptService.passwordAccepted(staff);
        return staff;
    }

    private LoginResponse issueTokens(Staff staff) {
        return issueTokens(staff, null);
    }

    // A collector's session is bound to their phone (deviceId); office staff pass null.
    private LoginResponse issueTokens(Staff staff, String deviceId) {
        List<String> roles = roleNames(staff);
        return LoginResponse.builder()
                .staffName(staff.getFirstName().toUpperCase() + " " + staff.getLastName().toUpperCase())
                .roles(roles)
                .authToken(jwtUtility.generateAccessToken(staff.getStaffId(), roles))
                .refreshToken(sessionService.startSession(staff, deviceId))
                .build();
    }

    // SessionService checks the token, that the staff member is still active, and that they may
    // refresh on this endpoint, then swaps the token.
    private TokenResponse refresh(RefreshRequest refreshRequest, Predicate<List<Roles>> allowed) {
        SessionService.Rotation rotation = sessionService.rotate(refreshRequest.getRefreshToken(), allowed,
                refreshRequest.getDeviceId());
        List<String> roles = rotation.roles().stream().map(Enum::name).toList();
        return new TokenResponse(jwtUtility.generateAccessToken(rotation.staffId(), roles), rotation.refreshToken());
    }

    private static boolean isCollector(Staff staff) {
        return staff.getRoles() != null && staff.getRoles().stream().anyMatch(r -> r.getRole() == Roles.COLLECTOR);
    }

    private static List<String> roleNames(Staff staff) {
        return staff.getRoles().stream().map(role -> role.getRole().name()).toList();
    }
}
