package com.bynexlabs.bankingbackend.Utility;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter  extends OncePerRequestFilter {

    private final JwtUtility jwtUtility;

    private final StaffRepo staffRepo;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No bearer token, or the request is already authenticated: nothing to do.
        if (authHeader == null || !authHeader.startsWith("Bearer ")
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        if (!jwtUtility.isAccessToken(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtUtility.extractUsername(token);

            // Access tokens stay valid until they expire, so status and roles are read from the
            // database on every request rather than trusted from the token. Deactivation and role
            // changes therefore apply immediately (owner decision D32); unknown or inactive staff
            // are left unauthenticated (401). The token's roles claim is informational only.
            Staff staff = staffRepo.findWithRolesByStaffId(username)
                    .filter(found -> found.getStaffStatus() == StaffStatus.ACTIVE)
                    .orElse(null);

            if (staff != null) {
                List<GrantedAuthority> authorities = (staff.getRoles() == null ? List.<StaffRoles>of() : staff.getRoles())
                        .stream()
                        .map(role -> new SimpleGrantedAuthority(role.getRole().name()))
                        .collect(Collectors.toUnmodifiableList());

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                username,
                                null,
                                authorities
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // Defensive: never let token parsing bubble up as a 500.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
