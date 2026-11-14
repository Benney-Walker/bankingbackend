package com.bynexlabs.bankingbackend.DTO.settings;

import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialSettingsUpdateRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private FinancialSettingsUpdateRequest.FinancialSettingsUpdateRequestBuilder valid() {
        return FinancialSettingsUpdateRequest.builder()
                .initialShareAmount(new BigDecimal("50"))
                .withdrawalMinBalance(new BigDecimal("20"))
                .withdrawalDailyLimit(new BigDecimal("10000"))
                .withdrawalFeeEnabled(true)
                .withdrawalFeeType(WithdrawalFeeType.FLAT)
                .withdrawalFeeValue(new BigDecimal("2"))
                .version(0L);
    }

    private Set<String> invalidFields(FinancialSettingsUpdateRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestPasses() {
        assertThat(invalidFields(valid().build())).isEmpty();
    }

    // Zero is allowed: no mandatory shares, no minimum balance, or a free withdrawal.
    @Test
    void zeroIsAllowedWhereItMakesSense() {
        assertThat(invalidFields(valid()
                .initialShareAmount(BigDecimal.ZERO)
                .withdrawalMinBalance(BigDecimal.ZERO)
                .withdrawalFeeValue(BigDecimal.ZERO)
                .build())).isEmpty();
    }

    @Test
    void negativeAmountsAreRejected() {
        assertThat(invalidFields(valid()
                .initialShareAmount(new BigDecimal("-1"))
                .withdrawalMinBalance(new BigDecimal("-1"))
                .withdrawalFeeValue(new BigDecimal("-1"))
                .build()))
                .containsExactlyInAnyOrder("initialShareAmount", "withdrawalMinBalance", "withdrawalFeeValue");
    }

    @Test
    void dailyLimitMustBeAboveZero() {
        assertThat(invalidFields(valid().withdrawalDailyLimit(BigDecimal.ZERO).build()))
                .containsExactly("withdrawalDailyLimit");
    }

    @Test
    void moreThanFourDecimalPlacesIsRejected() {
        assertThat(invalidFields(valid().initialShareAmount(new BigDecimal("50.00001")).build()))
                .containsExactly("initialShareAmount");
    }

    @Test
    void everyFieldIsRequired() {
        assertThat(invalidFields(new FinancialSettingsUpdateRequest()))
                .containsExactlyInAnyOrder("initialShareAmount", "withdrawalMinBalance", "withdrawalDailyLimit",
                        "withdrawalFeeEnabled", "withdrawalFeeType", "withdrawalFeeValue", "version");
    }
}
