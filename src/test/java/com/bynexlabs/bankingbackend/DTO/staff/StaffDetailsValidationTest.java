package com.bynexlabs.bankingbackend.DTO.staff;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** add-staff takes no password (owner decision 2026-11-15): the backend sets a random one. */
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

    private static StaffDetails.StaffDetailsBuilder complete() {
        return StaffDetails.builder()
                .firstName("Kofi").lastName("Owusu").dateOfBirth("1988-02-20")
                .phoneNumber("0201234567").email("kofi@example.com")
                .residentialAddress("Takoradi").roles(List.of("AGENT")).branchId(UUID.randomUUID());
    }

    @Test
    void completeDetailsWithoutAPasswordAreValid() {
        assertThat(validator.validate(complete().build())).isEmpty();
    }

    @Test
    void branchIsStillRequired() {
        assertThat(validator.validate(complete().branchId(null).build()))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("branchId");
    }
}
