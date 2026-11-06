package com.bynexlabs.bankingbackend.Utility;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
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

            // Access tokens stay valid until they expire, so a staff member deactivated after
            // logging in could keep using theirs. Checking the current status on every request
            // closes that window; unknown or inactive staff are left unauthenticated (401).
            boolean active = staffRepo.findStatusByStaffId(username)
                    .map(status -> status == StaffStatus.ACTIVE)
                    .orElse(false);

            if (active) {
                List<String> roles = jwtUtility.extractRoles(token);

                List<GrantedAuthority> authorities = (roles == null ? List.<String>of() : roles).stream()
                        .map(SimpleGrantedAuthority::new)
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
