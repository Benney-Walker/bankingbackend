package com.bynexlabs.bankingbackend.DTO.member;

import com.bynexlabs.bankingbackend.enums.MemberUpdateStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberUpdateProposal;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A proposed member update with the member's current values beside the proposed ones, so the
 * administrator can compare. {@code rejectionReason} is set once rejected.
 */
public record MemberUpdateProposalResponse(UUID requestId, UUID memberId, String memberName, String branchName,
                                           MemberUpdateStatus status,
                                           Values current, Values proposed,
                                           String requestedByStaffId, LocalDateTime requestedAt,
                                           String reviewedByStaffId, LocalDateTime reviewedAt,
                                           String rejectionReason) {

    public record Values(String phoneNumber, String residentialAddress, String occupation) {
    }

    /** Must run inside a transaction: reads the member, branch and staff. */
    public static MemberUpdateProposalResponse from(MemberUpdateProposal proposal) {
        Member member = proposal.getMember();
        String middle = member.getMiddleName() == null || member.getMiddleName().isBlank() ? "" : member.getMiddleName().trim() + " ";
        return new MemberUpdateProposalResponse(
                proposal.getId(),
                member.getId(),
                member.getFirstName() + " " + middle + member.getLastName(),
                member.getBranch() == null ? null : member.getBranch().getName(),
                proposal.getStatus(),
                new Values(member.getPhoneNumber(), member.getResidentialAddress(), member.getOccupation()),
                new Values(proposal.getPhoneNumber(), proposal.getResidentialAddress(), proposal.getOccupation()),
                proposal.getRequestedBy().getStaffId(),
                proposal.getRequestedAt(),
                proposal.getReviewedBy() == null ? null : proposal.getReviewedBy().getStaffId(),
                proposal.getReviewedAt(),
                proposal.getRejectionReason());
    }
}
