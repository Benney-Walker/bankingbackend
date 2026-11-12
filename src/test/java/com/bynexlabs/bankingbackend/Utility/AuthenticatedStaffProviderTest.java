package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.Roles;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedStaffProviderTest {

    private final AuthenticatedStaffProvider provider = new AuthenticatedStaffProvider(null);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void hasRoleReadsTheCurrentAuthorities() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "900105", null, List.of(new SimpleGrantedAuthority("AGENT"))));

        assertThat(provider.hasRole(Roles.AGENT)).isTrue();
        assertThat(provider.hasRole(Roles.ADMINISTRATOR)).isFalse();
    }

    @Test
    void noAuthenticationMeansNoRole() {
        assertThat(provider.hasRole(Roles.ADMINISTRATOR)).isFalse();
    }
}
