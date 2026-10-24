package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

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
    @JoinColumn(name = "loan_application_id")
    private LoanApplication loanApplication;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_account_id", unique = true)
    private MemberAccount memberAccountId;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal principalAmount;

    @Column(name = "current_interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal currentInterestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "disbursement_date", nullable = false)
    private LocalDate disbursementDate;

    @Column(name = "maturity_date", nullable = false)
    private LocalDate maturityDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private LoanAccountStatus status;
}
