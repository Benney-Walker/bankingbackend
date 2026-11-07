package com.bynexlabs.bankingbackend.DTO.transaction;

import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A recorded transaction as returned to the apps. Recorded transactions are final (D16). */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TransactionResponse {

    private UUID transactionId;
    private String idempotencyKey;
    private TransactionType transactionType;
    private BigDecimal amount;
    private String accountNumber;
    private TransactionStatus status;
    private String description;
    private String initiatedByStaffId;
    private LocalDateTime createdAt;
    private LocalDateTime postedAt;

    /** Must be called inside a transaction: account and staff are loaded lazily. */
    public static TransactionResponse from(Transaction transaction) {
        return TransactionResponse.builder()
                .transactionId(transaction.getId())
                .idempotencyKey(transaction.getIdempotencyKey())
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .accountNumber(transaction.getMemberAccount() == null ? null : transaction.getMemberAccount().getAccountNumber())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .initiatedByStaffId(transaction.getInitiatedBy() == null ? null : transaction.getInitiatedBy().getStaffId())
                .createdAt(transaction.getCreatedAt())
                .postedAt(transaction.getPostedAt())
                .build();
    }
}
