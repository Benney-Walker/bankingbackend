package com.bynexlabs.bankingbackend.DTO.loan;

import com.bynexlabs.bankingbackend.enums.ApplicationStatus;
import com.bynexlabs.bankingbackend.models.LoanApplication;
import com.bynexlabs.bankingbackend.models.LoanGuarantor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A loan application. guarantors is filled when one application is opened; loanId once approved.
 * Must be built inside a transaction (reads the member, branch and product).
 */
public record LoanApplicationResponse(UUID id, UUID memberId, String memberName, String branchName,
                                      Integer productId, String productName, BigDecimal requestedAmount,
                                      Integer termMonths, BigDecimal approvedAmount, String purpose,
                                      ApplicationStatus status, String rejectionReason, String appliedBy,
                                      String reviewedBy, LocalDateTime createdAt, LocalDateTime reviewedAt,
                                      List<Guarantor> guarantors, UUID loanId) {

    public record Guarantor(UUID memberId, String name) {
    }

    public static LoanApplicationResponse from(LoanApplication application, List<LoanGuarantor> guarantors, UUID loanId) {
        var member = application.getMember();
        return new LoanApplicationResponse(application.getId(), member.getId(),
                member.getFirstName() + " " + member.getLastName(),
                member.getBranch() == null ? null : member.getBranch().getName(),
                application.getLoanProduct().getId(), application.getLoanProduct().getName(),
                application.getRequestedAmount(), application.getRequestedTermMonths(), application.getApprovedAmount(),
                application.getPurpose(), application.getStatus(), application.getRejectionReason(),
                application.getAppliedBy() == null ? null : application.getAppliedBy().getStaffId(),
                application.getReviewedBy() == null ? null : application.getReviewedBy().getStaffId(),
                application.getCreatedAt(), application.getReviewedAt(),
                guarantors == null ? null : guarantors.stream()
                        .map(g -> new Guarantor(g.getMember().getId(),
                                g.getMember().getFirstName() + " " + g.getMember().getLastName()))
                        .toList(),
                loanId);
    }
}
