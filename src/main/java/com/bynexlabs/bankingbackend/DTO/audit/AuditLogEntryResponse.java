package com.bynexlabs.bankingbackend.DTO.audit;

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.models.AuditLog;

import java.time.Instant;

/** One audit log entry. actorStaffId is null for automated actions and unverified sign-in attempts. */
public record AuditLogEntryResponse(Long id, Instant occurredAt, String actorStaffId, AuditAction action,
                                    AuditTargetType targetType, String targetId, String details) {

    public static AuditLogEntryResponse from(AuditLog entry) {
        return new AuditLogEntryResponse(entry.getId(), entry.getOccurredAt(), entry.getActorStaffId(), entry.getAction(),
                entry.getTargetType(), entry.getTargetId(), entry.getDetails());
    }
}
