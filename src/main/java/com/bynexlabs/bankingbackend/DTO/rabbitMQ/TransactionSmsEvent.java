package com.bynexlabs.bankingbackend.DTO.rabbitMQ;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A member's transaction SMS, written when the transaction is recorded so it shows the balance
 * right after that transaction, and sent once the transaction has committed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionSmsEvent {

    private UUID transactionId;

    /** The member's phone in the stored local format (0XXXXXXXXX); VynfySmsService adds +233. */
    private String phoneNumber;

    private String message;
}
