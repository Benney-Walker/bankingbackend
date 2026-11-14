package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.service.StaffService;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Roles come from the database on every request (D32), through StaffService.getStaffRoles, which
 * refuses inactive and unknown staff (collectors included, since they're staff accounts).
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "access-token";

    @Mock
    private JwtUtility jwtUtility;

    @Mock
    private StaffService staffService;

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

    @Test
    void activeStaffIsAuthenticatedWithRolesFromTheDatabase() throws Exception {
        givenValidTokenFor("900100");
        when(staffService.getStaffRoles("900100")).thenReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("AGENT")));

        runFilterWithToken();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("900100");
        assertThat(authentication.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("AGENT");
    }

    // D32: the token's roles are never used, so a removed role stops working on the next request.
    @Test
    void tokenRolesAreIgnored() throws Exception {
        givenValidTokenFor("900110");
        when(staffService.getStaffRoles("900110")).thenReturn(List.<GrantedAuthority>of(new SimpleGrantedAuthority("COLLECTOR")));

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("COLLECTOR");
        verify(jwtUtility, never()).extractRoles(any());
    }

    @Test
    void inactiveOrUnknownStaffIsNotAuthenticated() throws Exception {
        givenValidTokenFor("900101");
        when(staffService.getStaffRoles("900101")).thenThrow(new UnauthorizedException("Staff account inactive or not found"));

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenThatIsNotAnAccessTokenIsIgnored() throws Exception {
        when(jwtUtility.isAccessToken(TOKEN)).thenReturn(false);

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(staffService);
    }
}
