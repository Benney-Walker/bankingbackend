package com.bynexlabs.bankingbackend.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPdfTest {

    @Test
    void typeNamesReadAsWords() {
        assertThat(ReportPdf.label("COLLECTOR_HANDOVER")).isEqualTo("Collector handover");
        assertThat(ReportPdf.label("DEPOSIT")).isEqualTo("Deposit");
    }

    @Test
    void amountsHaveThousandsSeparatorsAndTwoDecimals() {
        assertThat(ReportPdf.money(new BigDecimal("12345.675"))).isEqualTo("12,345.68");
        assertThat(ReportPdf.money(null)).isEqualTo("0.00");
    }
}
