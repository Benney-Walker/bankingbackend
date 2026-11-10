package com.bynexlabs.bankingbackend.Utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {"0.0001", "1", "50.50", "12.3400000", "999999999999999.9999"})
    void acceptsPositiveAmountsThatFitTheColumn(String amount) {
        assertThat(MoneyRules.requireValidAmount(new BigDecimal(amount))).isEqualByComparingTo(amount);
    }

    @Test
    void rejectsMissingAmount() {
        assertThatThrownBy(() -> MoneyRules.requireValidAmount(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.0000", "-1", "-0.01"})
    void rejectsZeroAndNegativeAmounts(String amount) {
        assertThatThrownBy(() -> MoneyRules.requireValidAmount(new BigDecimal(amount)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount must be greater than zero");
    }

    @Test
    void rejectsMoreThanFourDecimalPlaces() {
        assertThatThrownBy(() -> MoneyRules.requireValidAmount(new BigDecimal("10.00001")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount can have at most 4 decimal places");
    }

    @Test
    void rejectsAmountsLargerThanTheColumn() {
        assertThatThrownBy(() -> MoneyRules.requireValidAmount(new BigDecimal("1000000000000000")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Amount is too large");
    }
}
