package com.bynexlabs.bankingbackend.DTO.location;

import com.bynexlabs.bankingbackend.enums.LocationTrigger;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A point on a collector's trail. A PAYMENT point carries the payment it was taken for, when the
 * payment has reached the backend (an offline payment may arrive after its point).
 */
public record LocationPointResponse(BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                                    Integer batteryPercent, LocationTrigger trigger,
                                    LocalDateTime recordedAt, LocalDateTime receivedAt, Payment payment) {

    public record Payment(UUID transactionId, TransactionType type, BigDecimal amount, TransactionStatus status,
                          String memberName, String accountNumber) {
    }
}
