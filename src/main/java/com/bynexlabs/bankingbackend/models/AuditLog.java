package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;

/** One business audit entry (V22). Append-only: no setters, and Hibernate never updates it. */
@Entity
@Immutable
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_staff_id", length = 50, updatable = false)
    private String actorStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 60, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 40, updatable = false)
    private AuditTargetType targetType;

    @Column(name = "target_id", nullable = false, length = 100, updatable = false)
    private String targetId;

    @Column(name = "details", updatable = false, columnDefinition = "TEXT")
    private String details;
}
