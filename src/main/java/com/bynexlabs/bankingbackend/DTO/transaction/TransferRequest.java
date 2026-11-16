package com.bynexlabs.bankingbackend.DTO.transaction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Moves money between two savings or current accounts, of the same member or of two members.
 * idempotencyKey works as in TransactionRequest: one per user action, re-sent on retries.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransferRequest {

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;

    @NotBlank(message = "Source account number is required")
    private String fromAccountNumber;

    @NotBlank(message = "Destination account number is required")
    private String toAccountNumber;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
}
