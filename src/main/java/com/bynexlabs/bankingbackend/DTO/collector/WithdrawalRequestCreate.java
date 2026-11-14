package com.bynexlabs.bankingbackend.DTO.collector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A member's withdrawal request, created by a collector. recordedAt works as for payments. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequestCreate {

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    private LocalDateTime recordedAt;
}
