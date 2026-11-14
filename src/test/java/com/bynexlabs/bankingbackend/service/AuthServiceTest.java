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

import com.bynexlabs.bankingbackend.DTO.auth.LoginRequest;
import com.bynexlabs.bankingbackend.DTO.auth.LoginResponse;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.springframework.security.authentication.BadCredentialsException;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
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

        assertThatThrownBy(() -> authService.refreshStaffAccessToken(new RefreshRequest("not-a-refresh-token")))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid refresh token");
        verifyNoInteractions(staffService);
    }

    @Test
    void refreshRejectsTokenForUnknownStaff() {
        when(jwtUtility.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtility.extractUsername("refresh-token")).thenReturn("900199");
        when(staffService.getStaffDetails("900199")).thenReturn(null);

        assertThatThrownBy(() -> authService.refreshStaffAccessToken(new RefreshRequest("refresh-token")))
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

        assertThatThrownBy(() -> authService.refreshStaffAccessToken(new RefreshRequest("refresh-token")))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }

    // --- staff vs collector sign-in (owner decision 2026-11-14) ---

    private static Staff staffWithRoles(String staffId, Roles... roles) {
        Staff staff = Staff.builder().staffId(staffId).firstName("Esi").lastName("Boateng")
                .staffStatus(StaffStatus.ACTIVE).build();
        List<StaffRoles> rows = new ArrayList<>();
        for (Roles role : roles) {
            StaffRoles row = new StaffRoles();
            row.setStaff(staff);
            row.setRole(role);
            rows.add(row);
        }
        staff.setRoles(rows);
        return staff;
    }

    private static LoginRequest login(String staffId) {
        return LoginRequest.builder().staffId(staffId).password("long-enough-1").build();
    }

    @Test
    void collectorLoginIssuesTokensForACollector() {
        when(staffService.getStaffDetails("900110")).thenReturn(staffWithRoles("900110", Roles.COLLECTOR));
        when(jwtUtility.generateAccessToken("900110", List.of("COLLECTOR"))).thenReturn("access");
        when(jwtUtility.generateRefreshToken("900110")).thenReturn("refresh");

        LoginResponse response = authService.collectorAuthentication(login("900110"));

        verify(authenticationManager).authenticate(any());
        assertThat(response.getRoles()).containsExactly("COLLECTOR");
        assertThat(response.getAuthToken()).isEqualTo("access");
        assertThat(response.getRefreshToken()).isEqualTo("refresh");
        assertThat(response.getStaffName()).isEqualTo("ESI BOATENG");
    }

    @Test
    void collectorLoginRefusesOtherStaff() {
        when(staffService.getStaffDetails("900105")).thenReturn(staffWithRoles("900105", Roles.AGENT));

        assertThatThrownBy(() -> authService.collectorAuthentication(login("900105")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only collector accounts can sign in here");
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }

    @Test
    void staffLoginRefusesCollectors() {
        when(staffService.getStaffDetails("900110")).thenReturn(staffWithRoles("900110", Roles.COLLECTOR));

        assertThatThrownBy(() -> authService.staffAuthentication(login("900110")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Collectors sign in through the collector app");
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }

    // A wrong password stops at the AuthenticationManager, before any staff lookup.
    @Test
    void wrongPasswordFailsBeforeAnyLookup() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("Bad credentials"));

        assertThatThrownBy(() -> authService.collectorAuthentication(login("900110")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(staffService);
    }

    @Test
    void collectorRefreshIssuesANewAccessToken() {
        when(jwtUtility.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtility.extractUsername("refresh-token")).thenReturn("900110");
        when(staffService.getStaffDetails("900110")).thenReturn(staffWithRoles("900110", Roles.COLLECTOR));
        when(jwtUtility.generateAccessToken("900110", List.of("COLLECTOR"))).thenReturn("new-access");

        assertThat(authService.refreshCollectorAccessToken(new RefreshRequest("refresh-token"))).isEqualTo("new-access");
    }

    @Test
    void collectorRefreshRefusesAStaffToken() {
        when(jwtUtility.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtility.extractUsername("refresh-token")).thenReturn("900105");
        when(staffService.getStaffDetails("900105")).thenReturn(staffWithRoles("900105", Roles.AGENT));

        assertThatThrownBy(() -> authService.refreshCollectorAccessToken(new RefreshRequest("refresh-token")))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }

    @Test
    void staffRefreshRefusesACollectorToken() {
        when(jwtUtility.isRefreshToken("refresh-token")).thenReturn(true);
        when(jwtUtility.extractUsername("refresh-token")).thenReturn("900110");
        when(staffService.getStaffDetails("900110")).thenReturn(staffWithRoles("900110", Roles.COLLECTOR));

        assertThatThrownBy(() -> authService.refreshStaffAccessToken(new RefreshRequest("refresh-token")))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }
}
