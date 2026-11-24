package com.bynexlabs.bankingbackend.DTO.loan;

import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import com.bynexlabs.bankingbackend.models.LoanProduct;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public record LoanProductResponse(Integer id, String name, BigDecimal interestRatePercent, BigDecimal minAmount,
                                  BigDecimal maxAmount, Integer maxTermMonths, LoanFeeType processingFeeType,
                                  BigDecimal processingFeeValue, Integer guarantorsRequired, boolean active,
                                  LocalDateTime updatedAt, String updatedBy) {

    public static LoanProductResponse from(LoanProduct product) {
        return new LoanProductResponse(product.getId(), product.getName(), percent(product.getBaseInterestRate()),
                product.getMinAmount(), product.getMaxAmount(), product.getMaxTermMonths(),
                product.getProcessingFeeType(), product.getProcessingFeeValue(), product.getGuarantorsRequired(),
                Boolean.TRUE.equals(product.getIsActive()), product.getUpdatedAt(), product.getUpdatedBy());
    }

    /** 0.1250 → 12.50 */
    public static BigDecimal percent(BigDecimal fraction) {
        return fraction.movePointRight(2).setScale(2, RoundingMode.HALF_UP);
    }
}
