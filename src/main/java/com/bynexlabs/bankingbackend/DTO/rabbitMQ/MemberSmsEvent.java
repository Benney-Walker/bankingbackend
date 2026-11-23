package com.bynexlabs.bankingbackend.DTO.rabbitMQ;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A member notification SMS that isn't about a transaction (first use: details updated after an
 * approved change, owner decision 2026-11-23). Queued in the business transaction and sent once
 * it has committed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberSmsEvent {

    private UUID memberId;

    /** The member's phone in the stored local format (0XXXXXXXXX); VynfySmsService adds +233. */
    private String phoneNumber;

    private String message;
}
