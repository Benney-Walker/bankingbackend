package com.bynexlabs.bankingbackend.DTO.collector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Paying an approved request at the office. idempotencyKey works as for any withdrawal. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayWithdrawalRequest {

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;
}
