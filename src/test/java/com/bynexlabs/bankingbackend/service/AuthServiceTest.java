package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private StaffService staffService;

    @Mock
    private JwtUtility jwtUtility;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    @Test
    void refreshRejectsTokenThatIsNotAValidRefreshToken() {
        when(jwtUtility.isRefreshToken("not-a-refresh-token")).thenReturn(false);

        assertThatThrownBy(() -> authService.refreshAccessToken(new RefreshRequest("not-a-refresh-token")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
        verifyNoInteractions(staffService);
    }

    @Test
    void refreshRejectsTokenForUnknownStaff() {
        when(jwtUtility.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtility.extractUsername("refresh-token")).thenReturn("900199");
        when(staffService.getStaffDetails("900199")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshAccessToken(new RefreshRequest("refresh-token")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid or expired refresh token");
        verify(staffService).getStaffDetails("900199");
    }

    @Test
    void refreshRejectsInactiveStaff() {
        Staff inactive = Staff.builder().staffId("900150").staffStatus(StaffStatus.INACTIVE).build();
        when(jwtUtility.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtility.extractUsername("refresh-token")).thenReturn("900150");
        when(staffService.getStaffDetails("900150")).thenReturn(inactive);

        assertThatThrownBy(() -> authService.refreshAccessToken(new RefreshRequest("refresh-token")))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }
}
