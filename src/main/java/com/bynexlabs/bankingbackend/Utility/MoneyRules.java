package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.exception.BadRequestException;

import java.math.BigDecimal;

/**
 * Amount checks shared by every money movement. Money is always BigDecimal; never float/double.
 * Amounts must be positive (V8 has a CHECK for this too) and fit the DECIMAL(19,4) columns.
 * Whether amounts are limited to 2 decimal places (pesewas) is a business rule not yet decided,
 * so only the storage limit of 4 is enforced.
 */
public final class MoneyRules {

    public static final int MAX_SCALE = 4;

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999999.9999");

    private MoneyRules() {
    }

    public static BigDecimal requireValidAmount(BigDecimal amount) {
        if (amount == null) {
            throw new BadRequestException("Amount is required");
        }
        if (amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > MAX_SCALE) {
            throw new BadRequestException("Amount can have at most " + MAX_SCALE + " decimal places");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BadRequestException("Amount is too large");
        }
        return amount;
    }
}
