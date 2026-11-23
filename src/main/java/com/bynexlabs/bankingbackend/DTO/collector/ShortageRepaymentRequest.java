package com.bynexlabs.bankingbackend.DTO.collector;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Cash a collector pays back against their shortage, recorded at the office. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortageRepaymentRequest {

    @NotNull(message = "amount is required")
    private BigDecimal amount;

    /** One per repayment, made by the desktop app; a retry with the same key returns the same repayment. */
    @NotBlank(message = "idempotencyKey is required")
    @Size(max = 100, message = "idempotencyKey is at most 100 characters")
    private String idempotencyKey;

    @Size(max = 255, message = "note is at most 255 characters")
    private String note;
}
