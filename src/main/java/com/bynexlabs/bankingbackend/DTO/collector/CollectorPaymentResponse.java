package com.bynexlabs.bankingbackend.DTO.collector;

import com.bynexlabs.bankingbackend.enums.CollectorPaymentType;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A collector payment. PENDING until correctableUntil, then COMPLETED (balance updated, member
 * texted), or FAILED with failureReason if it couldn't be applied. correctableUntil is null for
 * payments recorded offline, which are final on arrival.
 */
public record CollectorPaymentResponse(UUID transactionId, String idempotencyKey, CollectorPaymentType type,
                                       String accountNumber, String memberName, BigDecimal amount,
                                       TransactionStatus status, String failureReason, LocalDateTime createdAt,
                                       LocalDateTime recordedAt, LocalDateTime correctableUntil,
                                       LocalDateTime postedAt) {

    /** Must run inside a transaction: reads the account and member. */
    public static CollectorPaymentResponse from(Transaction transaction, LocalDateTime correctableUntil) {
        var account = transaction.getMemberAccount();
        return new CollectorPaymentResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getTransactionType() == TransactionType.SHARES ? CollectorPaymentType.SHARES : CollectorPaymentType.SAVINGS,
                account.getAccountNumber(),
                account.getMember().getFirstName() + " " + account.getMember().getLastName(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getFailureReason(),
                transaction.getCreatedAt(),
                transaction.getRecordedAt(),
                correctableUntil,
                transaction.getPostedAt());
    }
}
