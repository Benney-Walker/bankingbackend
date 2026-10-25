package com.bynexlabs.bankingbackend.Utility;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class CustomAuthEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

        String json = """
                {
                  "timestamp": "%s",
                  "status": 401,
                  "error": "Session expired. Login again",
                  "message": "%s",
                  "path": "%s"
                }
                """.formatted(
                LocalDateTime.now(),
                authException.getMessage(),
                request.getRequestURI()
        );

        //log.warn("Unauthorized request to {} {}: {}",
        //        request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.getWriter().write(json);
    }
}
