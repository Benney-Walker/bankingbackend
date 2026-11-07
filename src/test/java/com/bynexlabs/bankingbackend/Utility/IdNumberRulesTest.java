package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** D10: Ghana Card (ECOWAS_CARD) numbers must be GHA-#########-#; voter card format not set yet. */
class IdNumberRulesTest {

    @Test
    void acceptsWellFormedGhanaCardNumber() {
        assertThatCode(() -> IdNumberRules.requireValidFormat(KycDocumentType.ECOWAS_CARD, "GHA-123456789-0"))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"GHA123456789-0", "GHA-12345678-0", "GHA-1234567890-0", "GHA-123456789-01",
            "NGA-123456789-0", "GHA-12345678A-0", "123456789", "GHA-123456789"})
    void rejectsMalformedGhanaCardNumbers(String idNumber) {
        assertThatThrownBy(() -> IdNumberRules.requireValidFormat(KycDocumentType.ECOWAS_CARD, idNumber))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Ghana Card number must look like GHA-123456789-0");
    }

    @Test
    void voterCardHasNoFormatRuleYet() {
        assertThatCode(() -> IdNumberRules.requireValidFormat(KycDocumentType.VOTER_CARD, "ANY-VALUE-123"))
                .doesNotThrowAnyException();
    }
}
