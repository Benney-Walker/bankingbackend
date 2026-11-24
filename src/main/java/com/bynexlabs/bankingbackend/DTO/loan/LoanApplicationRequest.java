package com.bynexlabs.bankingbackend.DTO.loan;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A loan application entered by an agent or administrator for a member of any branch. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanApplicationRequest {

    @NotNull(message = "Member is required")
    private UUID memberId;

    @NotNull(message = "Loan product is required")
    private Integer productId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    @Digits(integer = 15, fraction = 2, message = "Amount is too large or has more than 2 decimal places")
    private BigDecimal amount;

    @NotNull(message = "Term in months is required")
    @Positive(message = "Term must be at least 1 month")
    private Integer termMonths;

    /** Other members guaranteeing the loan: as many as the product needs. */
    @Builder.Default
    @Size(max = 10, message = "At most 10 guarantors")
    private List<UUID> guarantorMemberIds = new ArrayList<>();

    @Size(max = 500, message = "Purpose can be at most 500 characters")
    private String purpose;
}
