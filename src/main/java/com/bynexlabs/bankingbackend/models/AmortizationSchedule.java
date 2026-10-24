package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

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
    @JoinColumn(name = "loan_account_id", nullable = false)
    private LoanAccount loanAccount;

    @Column(name = "installment_number", nullable = false)
    private Integer installmentNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "expected_principal", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedPrincipal;

    @Column(name = "expected_interest", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedInterest;

    @Column(name = "expected_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedTotal;

    @Column(name = "paid_principal", precision = 19, scale = 4)
    private BigDecimal paidPrincipal;

    @Column(name = "paid_interest", precision = 19, scale = 4)
    private BigDecimal paidInterest;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ScheduleStatus status;
}
