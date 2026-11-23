package com.bynexlabs.bankingbackend.DTO.location;

import com.bynexlabs.bankingbackend.enums.LocationTrigger;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One point from the collector app (owner decisions 2026-11-23). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationPointRequest {

    @NotNull(message = "latitude is required")
    @DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
    @DecimalMax(value = "90", message = "latitude must be between -90 and 90")
    private BigDecimal latitude;

    @NotNull(message = "longitude is required")
    @DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
    @DecimalMax(value = "180", message = "longitude must be between -180 and 180")
    private BigDecimal longitude;

    /** Accuracy in metres, as the phone reports it. */
    @NotNull(message = "accuracyMeters is required")
    @DecimalMin(value = "0", message = "accuracyMeters can't be negative")
    @DecimalMax(value = "999999", message = "accuracyMeters is too large")
    private BigDecimal accuracyMeters;

    /** 0–100, if the phone gives it. */
    @Min(value = 0, message = "batteryPercent must be between 0 and 100")
    @Max(value = 100, message = "batteryPercent must be between 0 and 100")
    private Integer batteryPercent;

    @NotNull(message = "trigger is required (EVERY_5_MIN or PAYMENT)")
    private LocationTrigger trigger;

    /** Required for a PAYMENT point: the payment's idempotency key. Absent otherwise. */
    @Size(max = 100, message = "paymentIdempotencyKey is at most 100 characters")
    private String paymentIdempotencyKey;

    /** The phone's time when the point was taken (Africa/Accra). */
    @NotNull(message = "recordedAt is required")
    private LocalDateTime recordedAt;
}
