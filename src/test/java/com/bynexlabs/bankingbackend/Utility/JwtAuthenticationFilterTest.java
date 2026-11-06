package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
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

    @Test
    void activeStaffIsAuthenticatedWithTokenRoles() throws Exception {
        givenValidTokenFor("900100");
        when(staffRepo.findStatusByStaffId("900100")).thenReturn(Optional.of(StaffStatus.ACTIVE));
        when(jwtUtility.extractRoles(TOKEN)).thenReturn(List.of("AGENT"));

        runFilterWithToken();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo("900100");
        assertThat(authentication.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("AGENT");
    }

    @Test
    void inactiveStaffWithValidTokenIsNotAuthenticated() throws Exception {
        givenValidTokenFor("900101");
        when(staffRepo.findStatusByStaffId("900101")).thenReturn(Optional.of(StaffStatus.INACTIVE));

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(jwtUtility, never()).extractRoles(any());
    }

    @Test
    void unknownStaffWithValidTokenIsNotAuthenticated() throws Exception {
        givenValidTokenFor("900999");
        when(staffRepo.findStatusByStaffId("900999")).thenReturn(Optional.empty());

        runFilterWithToken();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
