package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.audit.AuditLogEntryResponse;
import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.service.AuditLogService;
import com.bynexlabs.bankingbackend.service.LoginAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Audit log reading and account unlocking, ADMINISTRATOR only (owner decisions 2026-11-24). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSecurityController {

    private final AuthenticatedStaffProvider staffProvider;

    private final AuditLogService auditLogService;

    private final LoginAttemptService loginAttemptService;

    /**
     * Audit log entries, newest first. Every filter is optional: from/to are instants
     * (e.g. 2026-11-24T00:00:00Z), to exclusive.
     */
    @GetMapping("/v1/audit-log")
    public PagedModel<AuditLogEntryResponse> auditLog(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String actorStaffId,
            @RequestParam(required = false) AuditAction action,
            @RequestParam(required = false) AuditTargetType targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return auditLogService.search(from, to, actorStaffId, action, targetType, targetId, page, size);
    }

    /** Unlocks an account locked after 5 wrong passwords. 409 if it isn't locked. */
    @PostMapping("/v1/staff/{staffId}/unlock")
    public StaffResponse unlock(@PathVariable String staffId) {
        return loginAttemptService.unlock(staffId, staffProvider.getStaffId());
    }
}
