package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final StaffRepo staffRepo;

    private final JwtUtility jwtUtility;

    public ResponseEntity<?> staffAuthentication(LoginRequest loginRequest) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequest.getStaffId(),
                        loginRequest.getPassword()
                )
        );

        Staffs staff = staffService.getStaffDetails(loginRequest.getStaffId());

        List<String> rolesList = staff.getRoles().stream().map(
                role -> role.getStaffRole().name()).toList();
        if (rolesList.contains(StaffRoles.GENERAL_STAFF.name()) || staff.getStaffStatus() == StaffStatus.INACTIVE) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "message", "You are not allowed to access the system! Contact your admin"
            ));
        }


        String accessToken = jwtUtility.generateAccessToken(
                loginRequest.getStaffId(),
                rolesList
        );

        String refreshToken = jwtUtility.generateRefreshToken(
                loginRequest.getStaffId()
        );

        return ResponseEntity.ok(
                LoginResponse.builder()
                        .staffName(
                                staff.getFirstName().toUpperCase() + " " + staff.getLastName().toUpperCase()
                        )
                        .roles(rolesList)
                        .institutionName(staff.getInstitution().getInstitutionName().toUpperCase())
                        .authToken(accessToken)
                        .refreshToken(refreshToken)
                        .build()
        );
    }
}
