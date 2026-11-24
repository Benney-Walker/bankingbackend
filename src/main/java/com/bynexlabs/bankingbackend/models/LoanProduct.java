package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.InterestType;
import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A kind of loan administrators offer (owner decisions 2026-11-24, V6 + V46). Interest is flat at
 * a yearly rate: interest = amount x rate x months / 12.
 */
@Entity
@Table(name = "loan_product")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Yearly flat rate as a fraction: 0.1250 is 12.5% a year. */
    @Column(name = "base_interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal baseInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 50)
    @Builder.Default
    private InterestType interestType = InterestType.FLAT;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "max_term_months", nullable = false)
    private Integer maxTermMonths;

    /** From V6. Unused: a late installment is only marked overdue (owner decision 2026-11-24). */
    @Column(name = "penalty_rate", precision = 5, scale = 4)
    private BigDecimal penaltyRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_fee_type", nullable = false, length = 20)
    private LoanFeeType processingFeeType;

    /** GHS for FLAT, a percent (at most 100) for PERCENTAGE. Taken from the payout. */
    @Column(name = "processing_fee_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal processingFeeValue;

    /** How many other members must guarantee a loan of this product (recorded only). */
    @Column(name = "guarantors_required", nullable = false)
    private Integer guarantorsRequired;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
