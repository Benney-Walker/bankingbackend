package com.bynexlabs.bankingbackend.DTO.loan;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Approval: approvedAmount may be lower than requested; leave it out to approve the amount asked for. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApprovalRequest {

    @Positive(message = "Approved amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Approved amount is too large or has more than 2 decimal places")
    private BigDecimal approvedAmount;
}
