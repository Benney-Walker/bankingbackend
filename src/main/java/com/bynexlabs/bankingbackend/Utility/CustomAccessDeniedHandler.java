package com.bynexlabs.bankingbackend.Utility;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import com.bynexlabs.bankingbackend.service.PermissionDeniedRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    /** Audits the refusal (owner decision 2026-11-24). */
    private final PermissionDeniedRecorder permissionDeniedRecorder;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);

        String json = """
                {
                  "timestamp": "%s",
                  "status": 403,
                  "error": "Forbidden",
                  "message": "You do not have permission to perform this action",
                  "path": "%s"
                }
                """.formatted(
                LocalDateTime.now(),
                request.getRequestURI()
        );

        String principal = (request.getUserPrincipal() != null)
                ? request.getUserPrincipal().getName()
                : "anonymous";
        log.warn("Access denied for '{}' on {} {}: {}",
                principal, request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage());

        permissionDeniedRecorder.record(request, "Not allowed for this role");
        response.getWriter().write(json);
    }
}
