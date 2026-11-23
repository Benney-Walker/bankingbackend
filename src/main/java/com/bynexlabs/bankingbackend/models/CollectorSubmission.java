package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A collector's daily cash submission (V39, owner decisions 2026-11-15). The collector submits
 * to close the day; it covers every payment not yet submitted. COMPLETED payments make up the
 * expected cash; FAILED ones are counted separately for the approver. Approval records the cash
 * counted and any surplus, and is final. Shortages are refused since 2026-11-23, so
 * {@code shortageAmount} stays 0 (the column is kept for earlier rows).
 */
@Entity
@Table(name = "collector_submission")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectorSubmission {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collector_id", nullable = false, updatable = false)
    private Staff collector;

    /** The collector's branch when they submitted: whose agent may approve. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "branch_id", nullable = false, updatable = false)
    private Branch branch;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CollectorSubmissionStatus status;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    /** COMPLETED payments included. */
    @Column(name = "payment_count", nullable = false)
    private int paymentCount;

    /** Total of the COMPLETED payments: the cash the collector should hand in. */
    @Column(name = "expected_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedAmount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "failed_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal failedAmount;

    @Column(name = "counted_amount", precision = 19, scale = 4)
    private BigDecimal countedAmount;

    @Builder.Default
    @Column(name = "shortage_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal shortageAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "overage_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal overageAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private Staff approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** The handover posted to the ledger; null when no cash was counted. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handover_transaction_id")
    private Transaction handoverTransaction;
}
