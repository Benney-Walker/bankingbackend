package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.LocationTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One location point from a collector's phone (V41, owner decisions 2026-11-23). Read-only here:
 * points are written with an insert that skips re-sent duplicates (CollectorLocationRepo).
 */
@Entity
@Table(name = "collector_location")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectorLocation {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "collector_id", nullable = false)
    private UUID collectorId;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "accuracy_m", nullable = false, precision = 8, scale = 2)
    private BigDecimal accuracyMeters;

    @Column(name = "battery_percent")
    private Integer batteryPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger", nullable = false, length = 20)
    private LocationTrigger trigger;

    @Column(name = "payment_idempotency_key", length = 100)
    private String paymentIdempotencyKey;

    /** The phone's time when the point was taken. */
    @Column(name = "recorded_at", nullable = false)
    private LocalDateTime recordedAt;

    /** The server's time when the point arrived. */
    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
