package com.bynexlabs.bankingbackend.DTO.member;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class MemberDetailsValidationTest {

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

    private static MemberDetails.MemberDetailsBuilder valid() {
        return MemberDetails.builder()
                .firstName("Ama").lastName("Mensah").dob("1990-05-14").phoneNumber("0241234567")
                .residentialAddress("Tarkwa").occupation("Trader").accountType("SAVINGS").branchId(UUID.randomUUID());
    }

    private Set<String> invalidFields(MemberDetails details) {
        return validator.validate(details).stream()
                .map(ConstraintViolation::getPropertyPath).map(Object::toString).collect(Collectors.toSet());
    }

    // A @NotBlank on the UUID made validate() throw UnexpectedTypeException for every registration.
    @Test
    void validRegistrationPasses() {
        assertThat(invalidFields(valid().build())).isEmpty();
    }

    @Test
    void branchIsRequired() {
        assertThat(invalidFields(valid().branchId(null).build())).containsExactly("branchId");
    }
}
