package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Audits requests refused with 403 (owner decision 2026-11-24): who, which request and why. Used by
 * the security filter's access-denied handler and by GlobalExceptionHandler. Never fails the
 * response: if the entry can't be written, that's logged and the 403 still goes out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionDeniedRecorder {

    private final AuditLogService auditLogService;

    public void record(HttpServletRequest request, String reason) {
        String actor = request.getUserPrincipal() == null ? null : request.getUserPrincipal().getName();
        String target = request.getMethod() + " " + request.getRequestURI();
        try {
            auditLogService.recordOnItsOwn(actor, AuditAction.PERMISSION_DENIED, AuditTargetType.REQUEST,
                    target.length() > 100 ? target.substring(0, 100) : target, reason);
        } catch (RuntimeException e) {
            log.error("Couldn't audit a refused request ({})", target, e);
        }
    }
}
