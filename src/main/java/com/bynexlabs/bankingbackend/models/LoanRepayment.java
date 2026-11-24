package com.bynexlabs.bankingbackend.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * An amount taken for one installment (V46), with its split for the ledger: proportional to the
 * installment (owner decision 2026-11-24). The member only sees the total.
 */
@Entity
@Table(name = "loan_repayment")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanRepayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_account_id", nullable = false, updatable = false)
    private LoanAccount loanAccount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "amortization_schedule_id", nullable = false, updatable = false)
    private AmortizationSchedule installment;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private Transaction transaction;

    @Column(name = "principal_part", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal principalPart;

    @Column(name = "interest_part", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal interestPart;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
