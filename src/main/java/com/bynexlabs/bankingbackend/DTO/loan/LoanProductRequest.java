package com.bynexlabs.bankingbackend.DTO.loan;

import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Creates or replaces a loan product (administrators). Interest is flat, at a yearly rate. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanProductRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name can be at most 100 characters")
    private String name;

    /** Yearly flat rate in percent, e.g. 12.5. */
    @NotNull(message = "Interest rate is required")
    @PositiveOrZero(message = "Interest rate can't be negative")
    @Digits(integer = 3, fraction = 2, message = "Interest rate must be below 1000% with at most 2 decimal places")
    private BigDecimal interestRatePercent;

    @NotNull(message = "Minimum amount is required")
    @Positive(message = "Minimum amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Minimum amount is too large or has more than 2 decimal places")
    private BigDecimal minAmount;

    @NotNull(message = "Maximum amount is required")
    @Positive(message = "Maximum amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Maximum amount is too large or has more than 2 decimal places")
    private BigDecimal maxAmount;

    @NotNull(message = "Maximum term is required")
    @Positive(message = "Maximum term must be at least 1 month")
    @Max(value = 360, message = "Maximum term can't be more than 360 months")
    private Integer maxTermMonths;

    @NotNull(message = "Processing fee type is required (FLAT or PERCENTAGE)")
    private LoanFeeType processingFeeType;

    @NotNull(message = "Processing fee value is required (0 for none)")
    @PositiveOrZero(message = "Processing fee can't be negative")
    @Digits(integer = 15, fraction = 2, message = "Processing fee is too large or has more than 2 decimal places")
    private BigDecimal processingFeeValue;

    @NotNull(message = "Number of guarantors is required (0 for none)")
    @PositiveOrZero(message = "Number of guarantors can't be negative")
    @Max(value = 10, message = "At most 10 guarantors")
    private Integer guarantorsRequired;

    @NotNull(message = "Say whether the product is active")
    private Boolean active;
}
