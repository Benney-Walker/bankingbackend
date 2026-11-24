package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Loan arithmetic (owner decisions 2026-11-24):
 * - flat interest at a yearly rate: interest = amount x rate x months / 12, to 2 decimal places;
 * - monthly installments due on the payout day of each month (the last day of a shorter month);
 * - the processing fee is flat or a percentage of the approved amount, taken from the payout.
 *
 * Rounding (Claude's choice, easy to change): every installment but the last carries the amount
 * and the interest divided by the months, rounded down to the pesewa; the last carries what's left,
 * so the installments always add up exactly to the loan and its interest.
 */
public final class LoanRules {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");

    private LoanRules() {
    }

    public record Installment(int number, LocalDate dueDate, BigDecimal principal, BigDecimal interest) {

        public BigDecimal total() {
            return principal.add(interest);
        }
    }

    /** How an amount collected for an installment splits for the ledger. */
    public record Split(BigDecimal principal, BigDecimal interest) {
    }

    public static BigDecimal totalInterest(BigDecimal amount, BigDecimal yearlyRate, int months) {
        return amount.multiply(yearlyRate).multiply(BigDecimal.valueOf(months))
                .divide(MONTHS_IN_YEAR, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal processingFee(LoanFeeType type, BigDecimal value, BigDecimal amount) {
        if (type == LoanFeeType.PERCENTAGE) {
            return amount.multiply(value).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        }
        return value;
    }

    public static List<Installment> schedule(BigDecimal amount, BigDecimal totalInterest, int months, LocalDate payoutDate) {
        if (months <= 0) {
            throw new IllegalArgumentException("A loan needs at least one month");
        }
        BigDecimal count = BigDecimal.valueOf(months);
        BigDecimal principalEach = amount.divide(count, 2, RoundingMode.DOWN);
        BigDecimal interestEach = totalInterest.divide(count, 2, RoundingMode.DOWN);
        List<Installment> installments = new ArrayList<>();
        BigDecimal principalSoFar = BigDecimal.ZERO;
        BigDecimal interestSoFar = BigDecimal.ZERO;
        for (int n = 1; n <= months; n++) {
            boolean last = n == months;
            BigDecimal principal = last ? amount.subtract(principalSoFar) : principalEach;
            BigDecimal interest = last ? totalInterest.subtract(interestSoFar) : interestEach;
            installments.add(new Installment(n, payoutDate.plusMonths(n), principal, interest));
            principalSoFar = principalSoFar.add(principal);
            interestSoFar = interestSoFar.add(interest);
        }
        return installments;
    }

    /**
     * Splits an amount collected for an installment in the installment's own ratio of borrowed
     * money to interest (owner decision 2026-11-24). Works on running totals, so part payments
     * never drift: once the installment is fully paid, exactly its principal and interest are in.
     */
    public static Split split(AmortizationSchedule installment, BigDecimal amount) {
        BigDecimal remainingPrincipal = installment.getExpectedPrincipal().subtract(installment.getPaidPrincipal());
        BigDecimal remainingInterest = installment.getExpectedInterest().subtract(installment.getPaidInterest());
        if (amount.signum() <= 0 || amount.compareTo(remainingPrincipal.add(remainingInterest)) > 0) {
            throw new IllegalArgumentException("Amount must be positive and no more than what's left on the installment");
        }
        BigDecimal paidAfter = installment.paidTotal().add(amount);
        BigDecimal interestPart;
        if (paidAfter.compareTo(installment.getExpectedTotal()) == 0) {
            interestPart = remainingInterest;
        } else {
            BigDecimal interestDueSoFar = paidAfter.multiply(installment.getExpectedInterest())
                    .divide(installment.getExpectedTotal(), 4, RoundingMode.HALF_UP);
            interestPart = interestDueSoFar.subtract(installment.getPaidInterest())
                    .max(BigDecimal.ZERO).min(remainingInterest).min(amount);
        }
        BigDecimal principalPart = amount.subtract(interestPart);
        if (principalPart.compareTo(remainingPrincipal) > 0) {
            principalPart = remainingPrincipal;
            interestPart = amount.subtract(principalPart);
        }
        return new Split(principalPart, interestPart);
    }
}
