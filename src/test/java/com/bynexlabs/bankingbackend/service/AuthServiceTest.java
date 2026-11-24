package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.auth.RefreshRequest;
import com.bynexlabs.bankingbackend.DTO.auth.TokenResponse;
import com.bynexlabs.bankingbackend.Utility.JwtUtility;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.security.authentication.LockedException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private SessionService sessionService;

    @Mock
    private CollectorSubmissionService collectorSubmissionService;

    @Mock
    private CollectorDeviceService collectorDeviceService;

    @Mock
    private LoginAttemptService loginAttemptService;

    @InjectMocks
    private AuthService authService;

    // --- refresh: SessionService swaps the token; AuthService issues the access token -----

    @Test
    void staffRefreshReturnsANewAccessAndRefreshToken() {
        when(sessionService.rotate(eq("refresh-token"), any(), any()))
                .thenReturn(new SessionService.Rotation("900105", List.of(Roles.AGENT), "next-refresh"));
        when(jwtUtility.generateAccessToken("900105", List.of("AGENT"))).thenReturn("new-access");

        TokenResponse response = authService.refreshStaffAccessToken(new RefreshRequest("refresh-token"));

        assertThat(response.authToken()).isEqualTo("new-access");
        assertThat(response.refreshToken()).isEqualTo("next-refresh");
    }

    @Test
    void refreshFailuresFromTheSessionArePassedOn() {
        when(sessionService.rotate(eq("refresh-token"), any(), any()))
                .thenThrow(new UnauthorizedException("Invalid or expired refresh token"));

        assertThatThrownBy(() -> authService.refreshStaffAccessToken(new RefreshRequest("refresh-token")))
                .isInstanceOf(UnauthorizedException.class);
        verify(jwtUtility, never()).generateAccessToken(any(), any());
    }

    // Each endpoint tells SessionService who may refresh there.
    @Test
    @SuppressWarnings("unchecked")
    void staffRefreshOnlyAllowsNonCollectorsAndCollectorRefreshOnlyCollectors() {
        when(sessionService.rotate(eq("staff-token"), any(), any()))
                .thenReturn(new SessionService.Rotation("900105", List.of(Roles.AGENT), "a"));
        when(sessionService.rotate(eq("collector-token"), any(), any()))
                .thenReturn(new SessionService.Rotation("900110", List.of(Roles.COLLECTOR), "b"));

        authService.refreshStaffAccessToken(new RefreshRequest("staff-token"));
        authService.refreshCollectorAccessToken(new RefreshRequest("collector-token"));

        ArgumentCaptor<Predicate<List<Roles>>> staffRule = ArgumentCaptor.forClass(Predicate.class);
        verify(sessionService).rotate(eq("staff-token"), staffRule.capture(), any());
        assertThat(staffRule.getValue().test(List.of(Roles.AGENT))).isTrue();
        assertThat(staffRule.getValue().test(List.of(Roles.COLLECTOR))).isFalse();

        ArgumentCaptor<Predicate<List<Roles>>> collectorRule = ArgumentCaptor.forClass(Predicate.class);
        verify(sessionService).rotate(eq("collector-token"), collectorRule.capture(), any());
        assertThat(collectorRule.getValue().test(List.of(Roles.COLLECTOR))).isTrue();
        assertThat(collectorRule.getValue().test(List.of(Roles.ADMINISTRATOR))).isFalse();
    }

    @Test
    void logoutEndsTheSession() {
        authService.logout(new RefreshRequest("refresh-token"));

        verify(sessionService).endSession("refresh-token");
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
        return LoginRequest.builder().staffId(staffId).password("long-enough-1").deviceId("phone-1").build();
    }

    @Test
    void collectorLoginIssuesTokensForACollector() {
        when(staffService.getStaffDetails("900110")).thenReturn(staffWithRoles("900110", Roles.COLLECTOR));
        when(jwtUtility.generateAccessToken("900110", List.of("COLLECTOR"))).thenReturn("access");
        when(sessionService.startSession(any(Staff.class), eq("phone-1"))).thenReturn("refresh");

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
        // Owner decisions 2026-11-24: counted towards the lockout and audited.
        verify(loginAttemptService).wrongPassword("900110");
    }

    @Test
    void lockedAccountIsRefusedAndAudited() {
        when(authenticationManager.authenticate(any())).thenThrow(new LockedException("locked"));

        assertThatThrownBy(() -> authService.staffAuthentication(login("900105"))).isInstanceOf(LockedException.class);
        verify(loginAttemptService).refused("900105", "Account locked");
        verify(loginAttemptService, never()).wrongPassword(any());
    }

    @Test
    void successfulSignInResetsTheCountAndIsAudited() {
        Staff staff = staffWithRoles("900105", Roles.AGENT);
        when(staffService.getStaffDetails("900105")).thenReturn(staff);

        authService.staffAuthentication(login("900105"));

        verify(loginAttemptService).passwordAccepted(staff);
        verify(loginAttemptService).signedIn(staff, "desktop app");
    }

    // Owner decision 2026-11-15: once today's submission is approved, no sign-in until tomorrow.
    @Test
    void collectorClosedForTodayCantSignIn() {
        Staff collector = staffWithRoles("900110", Roles.COLLECTOR);
        collector.setId(java.util.UUID.randomUUID());
        when(staffService.getStaffDetails("900110")).thenReturn(collector);
        when(collectorSubmissionService.isClosedForToday(collector.getId())).thenReturn(true);

        assertThatThrownBy(() -> authService.collectorAuthentication(login("900110")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Your collections for today have been approved. You can sign in again tomorrow");
        verifyNoInteractions(sessionService);
    }

    // --- one phone per collector (owner decisions 2026-11-23) --------------------------------

    @Test
    void collectorLoginChecksThePhoneAndBindsTheSessionToIt() {
        Staff collector = staffWithRoles("900110", Roles.COLLECTOR);
        when(staffService.getStaffDetails("900110")).thenReturn(collector);
        when(sessionService.startSession(collector, "phone-1")).thenReturn("refresh");

        authService.collectorAuthentication(login("900110"));

        verify(collectorDeviceService).requireRegisteredDevice(collector, "phone-1");
    }

    @Test
    void collectorOnAnotherPhoneGetsNoTokens() {
        Staff collector = staffWithRoles("900110", Roles.COLLECTOR);
        when(staffService.getStaffDetails("900110")).thenReturn(collector);
        org.mockito.Mockito.doThrow(new ForbiddenException("This account is registered to another phone."))
                .when(collectorDeviceService).requireRegisteredDevice(collector, "phone-1");

        assertThatThrownBy(() -> authService.collectorAuthentication(login("900110")))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(sessionService);
    }

    @Test
    void collectorRefreshPassesThePhone() {
        RefreshRequest request = new RefreshRequest("collector-token");
        request.setDeviceId("phone-1");
        when(sessionService.rotate(eq("collector-token"), any(), eq("phone-1")))
                .thenReturn(new SessionService.Rotation("900110", List.of(Roles.COLLECTOR), "next"));

        assertThat(authService.refreshCollectorAccessToken(request).refreshToken()).isEqualTo("next");
    }

    @Test
    void staffLoginDoesntCheckAPhone() {
        Staff agent = staffWithRoles("900105", Roles.AGENT);
        when(staffService.getStaffDetails("900105")).thenReturn(agent);

        authService.staffAuthentication(login("900105"));

        verifyNoInteractions(collectorDeviceService);
        verify(sessionService).startSession(agent, null);
    }
}
