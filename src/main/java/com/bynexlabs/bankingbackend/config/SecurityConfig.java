package com.bynexlabs.bankingbackend.config;

import com.bynexlabs.bankingbackend.Utility.CustomAccessDeniedHandler;
import com.bynexlabs.bankingbackend.Utility.CustomAuthEntryPoint;
import com.bynexlabs.bankingbackend.Utility.JwtAuthenticationFilter;
import com.bynexlabs.bankingbackend.enums.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
// Endpoint permissions are set by URL in filterChain below.
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    static final String[] API_DOCS_PATHS = {"/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**"};

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    private final CustomAccessDeniedHandler customAccessDeniedHandler;

    private final CustomAuthEntryPoint customAuthEntryPoint;

    // Browser origins allowed to call the API (comma-separated in app.cors.allowed-origins).
    // Origins never end with a slash; "https://example.app/" would never match a request.
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // API docs: served only when API_DOCS_ENABLED=true (otherwise 404).
                        .requestMatchers(API_DOCS_PATHS).permitAll()
                        // Actuator exposes internal details: administrators only, never public
                        // (owner decision 2026-11-07). Keep in step with management.endpoints.web.base-path.
                        .requestMatchers("/actuator/**").hasAuthority(Roles.ADMINISTRATOR.name())
                        .requestMatchers("/api/admin/**").hasAuthority(Roles.ADMINISTRATOR.name())
                        .requestMatchers("/api/member/**").hasAnyAuthority(Roles.ADMINISTRATOR.name(), Roles.AGENT.name())
                        // Only administrators create staff, collectors included (owner decision 2026-11-14).
                        // Must come before the /api/staff/** rule, which would otherwise match first.
                        .requestMatchers("/api/staff/v1/add-staff").hasAuthority(Roles.ADMINISTRATOR.name())
                        .requestMatchers("/api/staff/**").hasAnyAuthority(Roles.AGENT.name(), Roles.ADMINISTRATOR.name())
                        // Office money movements (owner decision 2026-11-14): administrators and agents.
                        // Collectors will record through their own flow later.
                        .requestMatchers("/api/transaction/**").hasAnyAuthority(Roles.ADMINISTRATOR.name(), Roles.AGENT.name())
                        .anyRequest().authenticated()
                ).sessionManagement( session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                ).addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                ).exceptionHandling(ex -> ex
                        .accessDeniedHandler(customAccessDeniedHandler)
                        .authenticationEntryPoint(customAuthEntryPoint)
                );

        return http.build();
    }
}
