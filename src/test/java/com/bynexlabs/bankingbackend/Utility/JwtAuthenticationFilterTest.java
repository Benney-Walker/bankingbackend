package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "access-token";

    @Mock
    private JwtUtility jwtUtility;

    @Mock
    private StaffRepo staffRepo;

    @InjectMocks
    private JwtAuthenticationFilter filter;

    @BeforeEach
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void runFilterWithToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/member/v1/add-member");
        request.addHeader("Authorization", "Bearer " + TOKEN);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private void givenValidTokenFor(String staffId) {
        when(jwtUtility.isAccessToken(TOKEN)).thenReturn(true);
        when(jwtUtility.extractUsername(TOKEN)).thenReturn(staffId);
    }

    private Staff staff(String staffId, StaffStatus status, Roles... roles) {
        Staff staff = Staff.builder().staffId(staffId).staffStatus(status).build();
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

    @Test
    void activeStaffIsAuthenticatedWithRolesFromTheDatabase() throws Exception {
        givenValidTokenFor("900100");
        when(staffRepo.findWithRolesByStaffId("900100"))
                .thenReturn(Optional.of(staff("900100", StaffStatus.ACTIVE, Roles.AGENT, Roles.COLLECTOR)));

        runFilterWithToken();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("900100");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("AGENT", "COLLECTOR");
    }

    // D32: a role removed by an administrator stops working on the next request, even though
    // the staff member's existing access token still carries the old role.
    @Test
    void tokenRolesAreIgnoredSoRoleRemovalIsImmediate() throws Exception {
        givenValidTokenFor("900102");
        when(staffRepo.findWithRolesByStaffId("900102"))
                .thenReturn(Optional.of(staff("900102", StaffStatus.ACTIVE, Roles.AGENT)));

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("AGENT");
        verify(jwtUtility, never()).extractRoles(any());
    }

    @Test
    void inactiveStaffWithValidTokenIsNotAuthenticated() throws Exception {
        givenValidTokenFor("900101");
        when(staffRepo.findWithRolesByStaffId("900101"))
                .thenReturn(Optional.of(staff("900101", StaffStatus.INACTIVE, Roles.ADMINISTRATOR)));

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownStaffWithValidTokenIsNotAuthenticated() throws Exception {
        givenValidTokenFor("900999");
        when(staffRepo.findWithRolesByStaffId("900999")).thenReturn(Optional.empty());

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
