package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The credit union's editable financial settings (V31): one row, changed by administrators
 * through the backend rather than in code. Read through FinancialSettingsService.
 */
@Entity
@Table(name = "financial_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSettings {

    /** The table holds a single row with this ID. */
    public static final int SINGLETON_ID = 1;

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "initial_share_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal initialShareAmount;

    /** A member's first deposit must be at least this much; never less than initialShareAmount (V32). */
    @Column(name = "min_first_deposit", nullable = false, precision = 19, scale = 4)
    private BigDecimal minFirstDeposit;

    @Column(name = "withdrawal_min_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal withdrawalMinBalance;

    @Column(name = "withdrawal_daily_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal withdrawalDailyLimit;

    @Column(name = "withdrawal_fee_enabled", nullable = false)
    private boolean withdrawalFeeEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "withdrawal_fee_type", nullable = false, length = 20)
    private WithdrawalFeeType withdrawalFeeType;

    @Column(name = "withdrawal_fee_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal withdrawalFeeValue;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 50)
    private String updatedBy;
}
