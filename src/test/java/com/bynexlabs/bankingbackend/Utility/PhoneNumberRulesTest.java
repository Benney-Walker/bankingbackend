package com.bynexlabs.bankingbackend.Utility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Owner rule: phone numbers are exactly 10 digits starting with 0. */
class PhoneNumberRulesTest {

    @Test
    void acceptsTenDigitsStartingWithZero() {
        assertThatCode(() -> PhoneNumberRules.requireLocalFormat("0241234567")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"241234567", "1241234567", "02412345678", "02412345ab", "+233241234567", "024 123 456"})
    void rejectsAnythingElse(String phoneNumber) {
        assertThatThrownBy(() -> PhoneNumberRules.requireLocalFormat(phoneNumber))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid phone number");
    }
}
