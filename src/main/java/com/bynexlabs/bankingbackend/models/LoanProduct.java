package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.InterestType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @Column(name = "base_interest_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal baseInterestRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "interest_type", nullable = false, length = 50)
    private InterestType interestType;

    @Column(name = "min_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal minAmount;

    @Column(name = "max_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxAmount;

    @Column(name = "max_term_months", nullable = false)
    private Integer maxTermMonths;

    @Column(name = "penalty_rate", precision = 5, scale = 4)
    private BigDecimal penaltyRate;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;
}
