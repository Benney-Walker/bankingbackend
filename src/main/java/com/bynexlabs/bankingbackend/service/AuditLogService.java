package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.AuditLogRepo;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.models.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Writes business audit entries (roadmap 8.1). Every method joins the caller's transaction
 * (MANDATORY): the entry is saved with the action it describes, and disappears with it if the
 * action rolls back. Never put passwords, reset codes or document contents in details.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_DETAILS_LENGTH = 2000;

    private final AuditLogRepo auditLogRepo;
    private final AuthenticatedStaffProvider authenticatedStaffProvider;

    /** Clock is replaceable in tests; the database column keeps the time zone. */
    Clock clock = Clock.systemUTC();

    /** An action by the staff member making the current request. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(AuditAction action, AuditTargetType targetType, Object targetId, String details) {
        save(authenticatedStaffProvider.getStaffId(), action, targetType, targetId, details);
    }

    /** An action by a named staff member who isn't the request's authenticated user (e.g. password reset). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordAs(String actorStaffId, AuditAction action, AuditTargetType targetType, Object targetId, String details) {
        save(actorStaffId, action, targetType, targetId, details);
    }

    /** An automated action with no staff member behind it (e.g. automated KYC verification). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordSystem(AuditAction action, AuditTargetType targetType, Object targetId, String details) {
        save(null, action, targetType, targetId, details);
    }

    private void save(String actorStaffId, AuditAction action, AuditTargetType targetType, Object targetId, String details) {
        if (action == null || targetType == null || targetId == null) {
            throw new IllegalArgumentException("Audit action, target type and target ID are required");
        }
        auditLogRepo.save(AuditLog.builder()
                .occurredAt(Instant.now(clock))
                .actorStaffId(actorStaffId)
                .action(action)
                .targetType(targetType)
                .targetId(targetId.toString())
                .details(truncate(details))
                .build());
    }

    private static String truncate(String details) {
        if (details == null || details.length() <= MAX_DETAILS_LENGTH) {
            return details;
        }
        return details.substring(0, MAX_DETAILS_LENGTH - 3) + "...";
    }
}
