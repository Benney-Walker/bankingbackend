package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoanRulesTest {

    // Owner decision 2026-11-24: flat, yearly rate. GHS 1,000 at 10% for 6 months = GHS 50.
    @Test
    void flatInterestIsAYearlyRate() {
        assertThat(LoanRules.totalInterest(new BigDecimal("1000"), new BigDecimal("0.1000"), 6)).isEqualByComparingTo("50.00");
        assertThat(LoanRules.totalInterest(new BigDecimal("1000"), new BigDecimal("0.1250"), 12)).isEqualByComparingTo("125.00");
        assertThat(LoanRules.totalInterest(new BigDecimal("500"), BigDecimal.ZERO, 3)).isEqualByComparingTo("0");
    }

    @Test
    void processingFeeIsFlatOrAPercentageOfTheLoan() {
        assertThat(LoanRules.processingFee(LoanFeeType.FLAT, new BigDecimal("20"), new BigDecimal("1000"))).isEqualByComparingTo("20");
        assertThat(LoanRules.processingFee(LoanFeeType.PERCENTAGE, new BigDecimal("2.5"), new BigDecimal("1001"))).isEqualByComparingTo("25.03");
    }

    // The installments add up exactly; the last one carries the rounding.
    @Test
    void installmentsAddUpAndTheLastCarriesTheRounding() {
        List<LoanRules.Installment> plan = LoanRules.schedule(new BigDecimal("1000"), new BigDecimal("50"), 3, LocalDate.of(2026, 11, 10));

        assertThat(plan).extracting(LoanRules.Installment::principal).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("333.33"), new BigDecimal("333.33"), new BigDecimal("333.34"));
        assertThat(plan).extracting(LoanRules.Installment::interest).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("16.66"), new BigDecimal("16.66"), new BigDecimal("16.68"));
        assertThat(plan).extracting(LoanRules.Installment::dueDate)
                .containsExactly(LocalDate.of(2026, 12, 10), LocalDate.of(2027, 1, 10), LocalDate.of(2027, 2, 10));
    }

    // Owner decision 2026-11-24: same day each month; a shorter month uses its last day.
    @Test
    void dueDateFallsBackToTheLastDayOfAShortMonth() {
        List<LoanRules.Installment> plan = LoanRules.schedule(new BigDecimal("300"), BigDecimal.ZERO, 3, LocalDate.of(2027, 1, 31));

        assertThat(plan).extracting(LoanRules.Installment::dueDate)
                .containsExactly(LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 31), LocalDate.of(2027, 4, 30));
    }

    private static AmortizationSchedule installment(String principal, String interest) {
        BigDecimal p = new BigDecimal(principal);
        BigDecimal i = new BigDecimal(interest);
        return AmortizationSchedule.builder().installmentNumber(1).dueDate(LocalDate.of(2026, 12, 10))
                .expectedPrincipal(p).expectedInterest(i).expectedTotal(p.add(i)).status(ScheduleStatus.PENDING).build();
    }

    // Owner's example: 40 loan + 10 interest = 50; 25 taken → 20 loan, 5 interest.
    @Test
    void partPaymentSplitsInTheInstallmentsRatio() {
        AmortizationSchedule installment = installment("40", "10");

        LoanRules.Split first = LoanRules.split(installment, new BigDecimal("25"));
        assertThat(first.principal()).isEqualByComparingTo("20");
        assertThat(first.interest()).isEqualByComparingTo("5");

        installment.setPaidPrincipal(first.principal());
        installment.setPaidInterest(first.interest());
        LoanRules.Split rest = LoanRules.split(installment, new BigDecimal("25"));
        assertThat(rest.principal()).isEqualByComparingTo("20");
        assertThat(rest.interest()).isEqualByComparingTo("5");
    }

    // Running totals: odd amounts never drift, and the final amount clears exactly.
    @Test
    void partPaymentsNeverDrift() {
        AmortizationSchedule installment = installment("333.34", "16.68");
        BigDecimal[] takes = {new BigDecimal("0.07"), new BigDecimal("123.45"), new BigDecimal("0.01"), new BigDecimal("226.49")};
        for (BigDecimal take : takes) {
            LoanRules.Split split = LoanRules.split(installment, take);
            assertThat(split.principal().add(split.interest())).isEqualByComparingTo(take);
            assertThat(split.principal().signum()).isGreaterThanOrEqualTo(0);
            assertThat(split.interest().signum()).isGreaterThanOrEqualTo(0);
            installment.setPaidPrincipal(installment.getPaidPrincipal().add(split.principal()));
            installment.setPaidInterest(installment.getPaidInterest().add(split.interest()));
        }
        assertThat(installment.getPaidPrincipal()).isEqualByComparingTo("333.34");
        assertThat(installment.getPaidInterest()).isEqualByComparingTo("16.68");
        assertThat(installment.remaining()).isEqualByComparingTo("0");
    }

    @Test
    void cantTakeMoreThanWhatsLeft() {
        assertThatThrownBy(() -> LoanRules.split(installment("40", "10"), new BigDecimal("50.01")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
