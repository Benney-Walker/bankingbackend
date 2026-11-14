package com.bynexlabs.bankingbackend.DTO.settings;

import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import com.bynexlabs.bankingbackend.models.FinancialSettings;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The current financial settings. A client that edits them sends version back in its update,
 * so an edit made from an out-of-date copy is rejected instead of silently overwriting.
 */
public record FinancialSettingsResponse(
        BigDecimal initialShareAmount,
        BigDecimal withdrawalMinBalance,
        BigDecimal withdrawalDailyLimit,
        boolean withdrawalFeeEnabled,
        WithdrawalFeeType withdrawalFeeType,
        BigDecimal withdrawalFeeValue,
        long version,
        Instant updatedAt,
        String updatedBy) {

    public static FinancialSettingsResponse from(FinancialSettings settings) {
        return new FinancialSettingsResponse(
                settings.getInitialShareAmount(),
                settings.getWithdrawalMinBalance(),
                settings.getWithdrawalDailyLimit(),
                settings.isWithdrawalFeeEnabled(),
                settings.getWithdrawalFeeType(),
                settings.getWithdrawalFeeValue(),
                settings.getVersion(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy());
    }
}
