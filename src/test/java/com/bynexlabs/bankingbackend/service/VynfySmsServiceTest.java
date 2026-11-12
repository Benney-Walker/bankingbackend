package com.bynexlabs.bankingbackend.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Vynfy only accepts international numbers; stored numbers are local (owner, 2026-11-07). */
class VynfySmsServiceTest {

    @ParameterizedTest
    @CsvSource({
            "0241234567,    +233241234567",
            "024 123 4567,  +233241234567",
            "024-123-4567,  +233241234567",
            "233241234567,  +233241234567",
            "+233241234567, +233241234567"
    })
    void convertsStoredNumbersToInternationalFormat(String stored, String expected) {
        assertThat(VynfySmsService.toInternationalFormat(stored)).isEqualTo(expected);
    }
}
