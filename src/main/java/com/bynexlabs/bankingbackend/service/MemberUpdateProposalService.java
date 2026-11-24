package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateProposalResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.MemberSmsEvent;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Repository.MemberUpdateProposalRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Utility.PhoneNumberRules;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.MemberUpdateStatus;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberUpdateProposal;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Member update approval (owner decisions 2026-11-23, increment #5):
 * - staff propose a new phone number, address and occupation for a member of any branch (the
 *   only fields that can change, owner rule 2026-11-07); nothing else changes yet;
 * - a member can have only one proposal waiting;
 * - an administrator approves (the change is applied as the direct update applies it, and the
 *   member is texted) or rejects with a reason the proposer sees;
 * - administrators can still update a member directly (AdminController).
 */
@Service
@RequiredArgsConstructor
public class MemberUpdateProposalService {

    private static final int MAX_PAGE_SIZE = 100;

    // No details in the text, only that something changed (owner decision 2026-11-23).
    static final String DETAILS_UPDATED_SMS =
            "Your member details have been updated. If you didn't ask for this change, please contact your branch.";

    private final MemberUpdateProposalRepo proposalRepo;

    private final MemberRepository memberRepository;

    private final MemberService memberService;

    private final StaffRepo staffRepo;

    private final AuditLogService auditLogService;

    private final OutboxService outboxService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    @Transactional
    public MemberUpdateProposalResponse propose(UUID memberId, MemberUpdateRequest request, Staff requester) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        PhoneNumberRules.requireLocalFormat(request.getPhoneNumber());
        if (memberRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), memberId)) {
            throw new ConflictException("A member with this phone number already exists.");
        }
        List<String> changed = changedFields(member, request);
        if (changed.isEmpty()) {
            throw new BadRequestException("Nothing to change: these are the member's current details");
        }
        if (proposalRepo.existsByMember_IdAndStatus(memberId, MemberUpdateStatus.PENDING)) {
            throw new ConflictException("This member already has a change waiting for approval");
        }

        MemberUpdateProposal proposal = proposalRepo.save(MemberUpdateProposal.builder()
                .id(UUID.randomUUID())
                .member(member)
                .requestedBy(staffRepo.getReferenceById(requester.getId()))
                .status(MemberUpdateStatus.PENDING)
                .phoneNumber(request.getPhoneNumber())
                .residentialAddress(request.getResidentialAddress())
                .occupation(request.getOccupation())
                .requestedAt(LocalDateTime.now(clock))
                .build());
        // Field names only, as for direct updates: the audit log doesn't hold contact details.
        auditLogService.record(AuditAction.MEMBER_UPDATE_REQUESTED, AuditTargetType.MEMBER, memberId,
                "Request " + proposal.getId() + ", proposed: " + String.join(", ", changed));
        return MemberUpdateProposalResponse.from(proposal);
    }

    /**
     * Applies the change and texts the member. Administrators are the top role (owner,
     * 2026-11-24), so they may also review a proposal they made themselves.
     */
    @Transactional
    public MemberUpdateProposalResponse approve(UUID requestId, Staff administrator) {
        MemberUpdateProposal proposal = lockForReview(requestId);
        UUID memberId = proposal.getMember().getId();

        // The same checks and audit entry as a direct update; a phone number taken since the
        // proposal was made is refused (409) and the proposal stays waiting.
        memberService.updateMemberContact(memberId, MemberUpdateRequest.builder()
                .phoneNumber(proposal.getPhoneNumber())
                .residentialAddress(proposal.getResidentialAddress())
                .occupation(proposal.getOccupation())
                .build());

        reviewed(proposal, MemberUpdateStatus.APPROVED, administrator, null);
        auditLogService.record(AuditAction.MEMBER_UPDATE_APPROVED, AuditTargetType.MEMBER, memberId,
                "Request " + proposal.getId());
        outboxService.enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.MEMBER_SMS_ROUTING_KEY,
                MemberSmsEvent.builder()
                        .memberId(memberId)
                        .phoneNumber(proposal.getPhoneNumber())
                        .message(DETAILS_UPDATED_SMS)
                        .build());
        return MemberUpdateProposalResponse.from(proposal);
    }

    @Transactional
    public MemberUpdateProposalResponse reject(UUID requestId, String reason, Staff administrator) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A rejection reason is required");
        }
        MemberUpdateProposal proposal = lockForReview(requestId);
        reviewed(proposal, MemberUpdateStatus.REJECTED, administrator, reason.trim());
        auditLogService.record(AuditAction.MEMBER_UPDATE_REJECTED, AuditTargetType.MEMBER,
                proposal.getMember().getId(), "Request " + proposal.getId());
        return MemberUpdateProposalResponse.from(proposal);
    }

    /** Administrators: proposals by status (default waiting ones), newest first. */
    @Transactional(readOnly = true)
    public PagedModel<MemberUpdateProposalResponse> list(MemberUpdateStatus status, int page, int size) {
        return new PagedModel<>(proposalRepo.findByStatus(status == null ? MemberUpdateStatus.PENDING : status,
                pageRequest(page, size)).map(MemberUpdateProposalResponse::from));
    }

    /** The signed-in staff member's own proposals and their outcome, newest first. */
    @Transactional(readOnly = true)
    public PagedModel<MemberUpdateProposalResponse> mine(Staff requester, int page, int size) {
        return new PagedModel<>(proposalRepo.findByRequestedBy_Id(requester.getId(), pageRequest(page, size))
                .map(MemberUpdateProposalResponse::from));
    }

    private MemberUpdateProposal lockForReview(UUID requestId) {
        MemberUpdateProposal proposal = proposalRepo.lockById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Update request not found"));
        if (proposal.getStatus() != MemberUpdateStatus.PENDING) {
            throw new ConflictException("This request was already " + proposal.getStatus().name().toLowerCase());
        }
        return proposal;
    }

    private void reviewed(MemberUpdateProposal proposal, MemberUpdateStatus status, Staff reviewer, String reason) {
        proposal.setStatus(status);
        proposal.setReviewedBy(staffRepo.getReferenceById(reviewer.getId()));
        proposal.setReviewedAt(LocalDateTime.now(clock));
        proposal.setRejectionReason(reason);
    }

    private static List<String> changedFields(Member member, MemberUpdateRequest request) {
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(member.getPhoneNumber(), request.getPhoneNumber())) changed.add("phoneNumber");
        if (!Objects.equals(member.getResidentialAddress(), request.getResidentialAddress())) changed.add("residentialAddress");
        if (!Objects.equals(member.getOccupation(), request.getOccupation())) changed.add("occupation");
        return changed;
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "requestedAt"));
    }
}
