package com.bynexlabs.bankingbackend.DTO.collector;

import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;
import com.bynexlabs.bankingbackend.models.CollectorSubmission;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A collector's daily submission. {@code expectedAmount} is the total of the completed payments
 * (the cash the collector should hand in); failed payments are counted separately. After
 * approval, {@code shortageAmount} or {@code overageAmount} shows any difference from the cash
 * counted. {@code payments} is filled only when one submission is fetched.
 */
public record CollectorSubmissionResponse(UUID submissionId, String collectorStaffId, String collectorName,
                                          String branchName, CollectorSubmissionStatus status,
                                          LocalDateTime submittedAt, int paymentCount, BigDecimal expectedAmount,
                                          int failedCount, BigDecimal failedAmount, BigDecimal countedAmount,
                                          BigDecimal shortageAmount, BigDecimal overageAmount,
                                          String approvedByStaffId, LocalDateTime approvedAt,
                                          List<CollectorPaymentResponse> payments) {

    /** Must run inside a transaction: reads the collector, branch and approver. */
    public static CollectorSubmissionResponse from(CollectorSubmission submission, List<CollectorPaymentResponse> payments) {
        var collector = submission.getCollector();
        return new CollectorSubmissionResponse(
                submission.getId(),
                collector.getStaffId(),
                collector.getFirstName() + " " + collector.getLastName(),
                submission.getBranch().getName(),
                submission.getStatus(),
                submission.getSubmittedAt(),
                submission.getPaymentCount(),
                submission.getExpectedAmount(),
                submission.getFailedCount(),
                submission.getFailedAmount(),
                submission.getCountedAmount(),
                submission.getShortageAmount(),
                submission.getOverageAmount(),
                submission.getApprovedBy() == null ? null : submission.getApprovedBy().getStaffId(),
                submission.getApprovedAt(),
                payments);
    }
}
