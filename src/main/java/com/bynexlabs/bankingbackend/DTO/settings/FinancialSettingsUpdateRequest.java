package com.bynexlabs.bankingbackend.DTO.settings;

import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A full replacement of the financial settings, for the desktop app's settings screen. Every
 * field is required, and version must be the one the client last read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialSettingsUpdateRequest {

    @NotNull(message = "Initial share amount is required")
    @PositiveOrZero(message = "Initial share amount can't be negative")
    @Digits(integer = 15, fraction = 4, message = "Initial share amount is too large or has more than 4 decimal places")
    private BigDecimal initialShareAmount;

    @NotNull(message = "Minimum first deposit is required")
    @PositiveOrZero(message = "Minimum first deposit can't be negative")
    @Digits(integer = 15, fraction = 4, message = "Minimum first deposit is too large or has more than 4 decimal places")
    private BigDecimal minFirstDeposit;

    @NotNull(message = "Minimum balance is required")
    @PositiveOrZero(message = "Minimum balance can't be negative")
    @Digits(integer = 15, fraction = 4, message = "Minimum balance is too large or has more than 4 decimal places")
    private BigDecimal withdrawalMinBalance;

    @NotNull(message = "Daily withdrawal limit is required")
    @Positive(message = "Daily withdrawal limit must be greater than zero")
    @Digits(integer = 15, fraction = 4, message = "Daily withdrawal limit is too large or has more than 4 decimal places")
    private BigDecimal withdrawalDailyLimit;

    @NotNull(message = "Say whether the withdrawal fee is on")
    private Boolean withdrawalFeeEnabled;

    @NotNull(message = "Withdrawal fee type is required (FLAT or PERCENTAGE)")
    private WithdrawalFeeType withdrawalFeeType;

    @NotNull(message = "Withdrawal fee value is required")
    @PositiveOrZero(message = "Withdrawal fee value can't be negative")
    @Digits(integer = 15, fraction = 4, message = "Withdrawal fee value is too large or has more than 4 decimal places")
    private BigDecimal withdrawalFeeValue;

    @NotNull(message = "Version is required; send the version you last read")
    private Long version;
}
