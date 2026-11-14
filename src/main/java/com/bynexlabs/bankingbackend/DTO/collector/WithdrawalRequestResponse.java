package com.bynexlabs.bankingbackend.DTO.collector;

import com.bynexlabs.bankingbackend.enums.WithdrawalRequestStatus;
import com.bynexlabs.bankingbackend.models.WithdrawalRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A withdrawal request. correctableUntil: until then the collector can still correct it and it
 * can't be reviewed; null once final (or for offline requests).
 */
public record WithdrawalRequestResponse(UUID requestId, String accountNumber, String memberName, BigDecimal amount,
                                        String description, WithdrawalRequestStatus status, String requestedByStaffId,
                                        LocalDateTime createdAt, LocalDateTime recordedAt,
                                        LocalDateTime correctableUntil, String reviewedByStaffId,
                                        LocalDateTime reviewedAt, String rejectionReason, UUID transactionId) {

    /** Must run inside a transaction: reads the account, member and staff. */
    public static WithdrawalRequestResponse from(WithdrawalRequest request, LocalDateTime correctableUntil) {
        var account = request.getMemberAccount();
        return new WithdrawalRequestResponse(
                request.getId(),
                account.getAccountNumber(),
                account.getMember().getFirstName() + " " + account.getMember().getLastName(),
                request.getAmount(),
                request.getDescription(),
                request.getStatus(),
                request.getRequestedBy().getStaffId(),
                request.getCreatedAt(),
                request.getRecordedAt(),
                correctableUntil,
                request.getReviewedBy() == null ? null : request.getReviewedBy().getStaffId(),
                request.getReviewedAt(),
                request.getRejectionReason(),
                request.getTransaction() == null ? null : request.getTransaction().getId());
    }
}
