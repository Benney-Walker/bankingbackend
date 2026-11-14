package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import com.bynexlabs.bankingbackend.models.FinancialSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FeeRulesTest {

    private FinancialSettings settings(boolean enabled, WithdrawalFeeType type, String value) {
        return FinancialSettings.builder()
                .withdrawalFeeEnabled(enabled)
                .withdrawalFeeType(type)
                .withdrawalFeeValue(new BigDecimal(value))
                .build();
    }

    @Test
    void noFeeWhenSwitchedOff() {
        assertThat(FeeRules.withdrawalFee(settings(false, WithdrawalFeeType.FLAT, "2"), new BigDecimal("100")))
                .isEqualByComparingTo("0");
    }

    @Test
    void flatFeeIsTheSameWhateverTheAmount() {
        assertThat(FeeRules.withdrawalFee(settings(true, WithdrawalFeeType.FLAT, "2"), new BigDecimal("5000")))
                .isEqualByComparingTo("2");
    }

    // Owner decision 2026-11-14: percentage fees are rounded to 2 decimal places (half up).
    @Test
    void percentageFeeIsRoundedToTwoDecimalPlaces() {
        assertThat(FeeRules.withdrawalFee(settings(true, WithdrawalFeeType.PERCENTAGE, "1.5"), new BigDecimal("333.33")))
                .isEqualTo(new BigDecimal("5.00"));
        assertThat(FeeRules.withdrawalFee(settings(true, WithdrawalFeeType.PERCENTAGE, "1"), new BigDecimal("10.50")))
                .isEqualTo(new BigDecimal("0.11"));
        assertThat(FeeRules.withdrawalFee(settings(true, WithdrawalFeeType.PERCENTAGE, "1"), new BigDecimal("10.40")))
                .isEqualTo(new BigDecimal("0.10"));
    }
}
