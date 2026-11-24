package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.audit.AuditLogEntryResponse;
import com.bynexlabs.bankingbackend.Repository.AuditLogRepo;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.models.AuditLog;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes and reads business audit entries (roadmap 8.1). The record methods join the caller's
 * transaction (MANDATORY): the entry is saved with the action it describes, and disappears with it
 * if the action rolls back. recordOnItsOwn is for refusals, which have no action to join. Never put
 * passwords, reset codes or document contents in details.
 *
 * Owner decisions 2026-11-24: administrators read the log with filters; entries are kept 7 years.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final int MAX_DETAILS_LENGTH = 2000;

    private static final int MAX_PAGE_SIZE = 100;

    /** Owner decision 2026-11-24. The database (V47) refuses to delete anything younger. */
    static final int RETENTION_YEARS = 7;

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

    /**
     * For something that didn't happen, such as a refused request (owner decision 2026-11-24):
     * written in its own transaction, so it's kept even though the request failed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordOnItsOwn(String actorStaffId, AuditAction action, AuditTargetType targetType, Object targetId,
                               String details) {
        save(actorStaffId, action, targetType, targetId, details);
    }

    /** Entries matching every filter given (each optional), newest first. Administrators only. */
    @Transactional(readOnly = true)
    public PagedModel<AuditLogEntryResponse> search(Instant from, Instant to, String actorStaffId, AuditAction action,
                                                    AuditTargetType targetType, String targetId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("from can't be after to");
        }
        Specification<AuditLog> filter = (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (from != null) {
                where.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                where.add(cb.lessThan(root.get("occurredAt"), to));
            }
            if (actorStaffId != null && !actorStaffId.isBlank()) {
                where.add(cb.equal(root.get("actorStaffId"), actorStaffId.trim()));
            }
            if (action != null) {
                where.add(cb.equal(root.get("action"), action));
            }
            if (targetType != null) {
                where.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null && !targetId.isBlank()) {
                where.add(cb.equal(root.get("targetId"), targetId.trim()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return new PagedModel<>(auditLogRepo.findAll(filter, pageRequest).map(AuditLogEntryResponse::from));
    }

    /** Deletes entries older than 7 years (owner decision 2026-11-24). Returns how many. */
    @Transactional
    public int deleteExpired() {
        Instant cutoff = ZonedDateTime.now(clock).minusYears(RETENTION_YEARS).toInstant();
        return auditLogRepo.deleteOlderThan(cutoff);
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
