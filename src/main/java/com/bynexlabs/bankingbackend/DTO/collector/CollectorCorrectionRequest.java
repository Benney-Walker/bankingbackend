package com.bynexlabs.bankingbackend.DTO.collector;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A collector's correction within the 10-minute window: a new amount, another of the same
 * member's accounts, or both. A field left out keeps its current value.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectorCorrectionRequest {

    private BigDecimal amount;

    private String accountNumber;
}
