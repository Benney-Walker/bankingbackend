package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A loan that was paid out (V6 + V46). memberAccount is the member's savings or current account it
 * was paid into and is repaid from. One ACTIVE loan per member.
 */
@Entity
@Table(name = "loan_account")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_application_id", nullable = false, updatable = false)
    private LoanApplication loanApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false, updatable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_account_id", nullable = false, updatable = false)
    private MemberAccount memberAccount;

    /** The approved amount borrowed. */
    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal principalAmount;

    /** The product's yearly flat rate when the loan was approved. */
    @Column(name = "current_interest_rate", nullable = false, precision = 5, scale = 4, updatable = false)
    private BigDecimal currentInterestRate;

    @Column(name = "total_interest", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal totalInterest;

    @Column(name = "processing_fee", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal processingFee;

    @Column(name = "term_months", nullable = false, updatable = false)
    private Integer termMonths;

    @Column(name = "disbursement_date", nullable = false, updatable = false)
    private LocalDate disbursementDate;

    /** The last installment's due date. */
    @Column(name = "maturity_date", nullable = false, updatable = false)
    private LocalDate maturityDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "disbursement_transaction_id")
    private Transaction disbursementTransaction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanAccountStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
