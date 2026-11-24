package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
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
 * One monthly installment of a loan (V6 + V46): due on the payout day of each month, taken from the
 * member's account by the daily job.
 */
@Entity
@Table(name = "amortization_schedule")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmortizationSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loan_account_id", nullable = false, updatable = false)
    private LoanAccount loanAccount;

    @Column(name = "installment_number", nullable = false, updatable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false, updatable = false)
    private LocalDate dueDate;

    @Column(name = "expected_principal", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal expectedPrincipal;

    @Column(name = "expected_interest", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal expectedInterest;

    @Column(name = "expected_total", nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal expectedTotal;

    @Column(name = "paid_principal", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidPrincipal = BigDecimal.ZERO;

    @Column(name = "paid_interest", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal paidInterest = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status;

    /** When the installment was fully collected. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    public BigDecimal paidTotal() {
        return paidPrincipal.add(paidInterest);
    }

    public BigDecimal remaining() {
        return expectedTotal.subtract(paidTotal());
    }
}
