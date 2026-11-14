package com.bynexlabs.bankingbackend.DTO.collector;

import com.bynexlabs.bankingbackend.enums.CollectorPaymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A payment a collector takes from a member: a savings deposit or a share purchase. The app
 * makes idempotencyKey once per payment and re-sends it on retries and offline sync.
 * recordedAt is set only for payments recorded offline: the app sends those after their 10
 * minutes have passed, so they're applied as soon as they arrive.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectorPaymentRequest {

    @NotBlank(message = "Idempotency key is required")
    @Size(max = 100, message = "Idempotency key must be at most 100 characters")
    private String idempotencyKey;

    @NotNull(message = "Type is required (SAVINGS or SHARES)")
    private CollectorPaymentType type;

    @NotBlank(message = "Account number is required")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;

    /** Device time, for payments recorded offline only. */
    private LocalDateTime recordedAt;
}
