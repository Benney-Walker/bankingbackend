package com.bynexlabs.bankingbackend.DTO.staff;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** add-staff uses the same password rule as password reset: 8 to 72 characters. */
class StaffDetailsValidationTest {

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

    private Set<ConstraintViolation<StaffDetails>> violationsForPassword(String password) {
        StaffDetails details = StaffDetails.builder()
                .firstName("Kofi").lastName("Owusu").dateOfBirth("1988-02-20")
                .phoneNumber("0201234567").email("kofi@example.com").password(password)
                .residentialAddress("Takoradi").roles(List.of("AGENT"))
                .build();
        return validator.validateProperty(details, "password");
    }

    @Test
    void sevenCharacterPasswordIsRejected() {
        assertThat(violationsForPassword("short7!")).isNotEmpty();
    }

    @Test
    void eightCharacterPasswordIsAccepted() {
        assertThat(violationsForPassword("eight8!!")).isEmpty();
    }

    @Test
    void passwordLongerThanBcryptLimitIsRejected() {
        assertThat(violationsForPassword("x".repeat(73))).isNotEmpty();
    }
}
