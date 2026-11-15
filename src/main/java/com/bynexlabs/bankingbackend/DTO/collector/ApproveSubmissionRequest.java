package com.bynexlabs.bankingbackend.DTO.collector;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** The cash the approver counted from the collector (GHS); 0 if nothing was handed in. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApproveSubmissionRequest {
    @NotNull(message = "countedAmount is required")
    private BigDecimal countedAmount;
}
