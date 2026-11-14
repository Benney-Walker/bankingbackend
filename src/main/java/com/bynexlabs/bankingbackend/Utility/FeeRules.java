package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import com.bynexlabs.bankingbackend.models.FinancialSettings;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Works out fees from the financial settings. Nothing about the fee itself is hard-coded. */
public final class FeeRules {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private FeeRules() {
    }

    /**
     * The withdrawal fee for this amount: zero when the fee is off, the flat value, or the
     * percentage of the amount rounded to 2 decimal places (owner decision 2026-11-14), half up.
     */
    public static BigDecimal withdrawalFee(FinancialSettings settings, BigDecimal amount) {
        if (!settings.isWithdrawalFeeEnabled()) {
            return BigDecimal.ZERO;
        }
        if (settings.getWithdrawalFeeType() == WithdrawalFeeType.PERCENTAGE) {
            return amount.multiply(settings.getWithdrawalFeeValue())
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return settings.getWithdrawalFeeValue();
    }
}
