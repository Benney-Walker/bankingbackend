package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.CorrectionRecordType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One correction a collector made within the 10-minute window (V37): the amount and account
 * before and after. Kept as its own record so the original is never lost (owner decision
 * 2026-11-07). Append-only.
 */
@Entity
@Immutable
@Table(name = "collector_correction")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CollectorCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 30)
    private CorrectionRecordType recordType;

    /** The transaction ID (PAYMENT) or withdrawal request ID. */
    @Column(name = "record_id", nullable = false)
    private UUID recordId;

    @Column(name = "previous_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal previousAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_account_id", nullable = false)
    private MemberAccount previousAccount;

    @Column(name = "new_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal newAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_account_id", nullable = false)
    private MemberAccount newAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "corrected_by", nullable = false)
    private Staff correctedBy;

    @Column(name = "corrected_at", nullable = false)
    private LocalDateTime correctedAt;
}
