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
 * The app keeps a new payment for 5 minutes, during which the collector can correct it, then
 * sends it with the phone's createdAt and updatedAt (owner decision 2026-11-23). The backend
 * applies it as soon as it arrives.
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

    /** Phone time the payment was recorded (Africa/Accra): decides the collector's day. */
    @NotNull(message = "createdAt is required")
    private LocalDateTime createdAt;

    /** Phone time of the last correction in the app; the same as createdAt if never corrected. */
    @NotNull(message = "updatedAt is required")
    private LocalDateTime updatedAt;
}
