package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberSummaryResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.Utility.PhoneNumberRules;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    private final OutboxService outboxService;

    private final MemberAccountRepo memberAccountRepo;

    private final AuditLogService auditLogService;

    private final BranchService branchService;

    // Upper bound on the search page size, so one request can't pull the whole table.
    static final int MAX_PAGE_SIZE = 100;

    public Member getMemberDetails(UUID memberId) {
        return memberRepository.findById(memberId).orElse(null);
    }

    @Transactional
    public ResponseEntity<?> registerNewMember(MemberDetails request, Staff registeredBy) {
        if (registeredBy == null) {
            throw new UnauthorizedException("Staff account not found or inactive");
        }

        Branch branch = branchService.getBranchDetails(request.getBranchId());

        // Validate the input before touching the database.
        AccountType accountType = parseSelectedAccountType(request.getAccountType());
        LocalDate dob = parseDateOfBirth(request.getDob());
        PhoneNumberRules.requireLocalFormat(request.getPhoneNumber());

        if (memberRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new ConflictException("A member with this phone number already exists.");
        }

        Member member = new Member();
        member.setFirstName(request.getFirstName());
        member.setMiddleName(request.getMiddleName());
        member.setLastName(request.getLastName());
        member.setDateOfBirth(dob);
        member.setBranch(branch);
        member.setPhoneNumber(request.getPhoneNumber());
        member.setResidentialAddress(request.getResidentialAddress());
        member.setOccupation(request.getOccupation());
        member.setMembershipStatus(MembershipStatus.PENDING);
        // Recorded because only this staff member may re-upload the member's KYC after a rejection.
        member.setRegisteredBy(registeredBy);
        Member savedMember = memberRepository.save(member);

        SharesAccountEvent sharesAccountEvent = SharesAccountEvent.builder()
                .memberId(savedMember.getId())
                .build();
        outboxService.enqueue(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.DEFAULT_SHARE_ACCOUNT_ROUTING_KEY, sharesAccountEvent);

        OtherAccountCreationEvent otherEvent = OtherAccountCreationEvent.builder()
                .memberId(savedMember.getId())
                .accountType(accountType)
                .build();
        outboxService.enqueue(RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.OTHER_ACCOUNT_CREATION_ROUTING_KEY, otherEvent);
        auditLogService.recordAs(registeredBy.getStaffId(), AuditAction.MEMBER_REGISTERED,
                AuditTargetType.MEMBER, savedMember.getId(), "Selected account " + accountType);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "Id", savedMember.getId()
        ));
    }


    /**
     * Member search by part of a name (first, middle, last, or first + last), part of a phone
     * number, or an exact account number (owner decisions 2026-11-23). Administrators and agents
     * search every branch: agents need it to serve any branch's member at the counter. For a
     * member of another branch an agent sees name, branch, status and accounts, but not the
     * phone number. A query is required: listing members belongs with member visibility (#4).
     */
    @Transactional(readOnly = true)
    public PagedModel<MemberSummaryResponse> searchMembers(String query, int page, int size, Staff viewer,
                                                           boolean viewerIsAdmin) {
        if (query == null || query.isBlank()) {
            throw new BadRequestException("Enter a name, phone number or account number to search");
        }
        Page<Member> members = memberRepository.search(query.trim(),
                pageRequest(page, size, Sort.by("lastName", "firstName")));
        UUID viewerBranchId = viewer.getBranch() == null ? null : viewer.getBranch().getId();
        return summaries(members, member -> viewerIsAdmin || (viewerBranchId != null && member.getBranch() != null
                && viewerBranchId.equals(member.getBranch().getId())));
    }

    /**
     * The member list (owner decisions 2026-11-23, increment #4): administrators see every
     * branch and may narrow to one; agents see only their own branch (asking for another branch
     * is 403). Optional membership-status filter; newest registrations first. Rows are the same as
     * search rows, phone included, since every row is visible to the viewer's branch or an admin.
     */
    @Transactional(readOnly = true)
    public PagedModel<MemberSummaryResponse> listMembers(MembershipStatus status, UUID branchId, int page, int size,
                                                         Staff viewer, boolean viewerIsAdmin) {
        UUID branch = branchId;
        if (!viewerIsAdmin) {
            if (viewer.getBranch() == null) {
                throw new ForbiddenException("You are not assigned to a branch");
            }
            if (branchId != null && !branchId.equals(viewer.getBranch().getId())) {
                throw new ForbiddenException("You can only list members of your own branch");
            }
            branch = viewer.getBranch().getId();
        }
        PageRequest pageRequest = pageRequest(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Member> members;
        if (branch == null) {
            members = status == null ? memberRepository.findAll(pageRequest)
                    : memberRepository.findByMembershipStatus(status, pageRequest);
        } else {
            members = status == null ? memberRepository.findByBranch_Id(branch, pageRequest)
                    : memberRepository.findByBranch_IdAndMembershipStatus(branch, status, pageRequest);
        }
        return summaries(members, member -> true);
    }

    // Search and list rows, with each page's accounts loaded in one query.
    private PagedModel<MemberSummaryResponse> summaries(Page<Member> members, Predicate<Member> showPhone) {
        Map<UUID, List<MemberAccount>> accounts = members.isEmpty() ? Map.of()
                : memberAccountRepo.findByMember_IdIn(members.map(Member::getId).toList()).stream()
                    .collect(Collectors.groupingBy(account -> account.getMember().getId()));
        return new PagedModel<>(members.map(member -> MemberSummaryResponse.from(member,
                accounts.getOrDefault(member.getId(), List.of()), showPhone.test(member))));
    }

    private static PageRequest pageRequest(int page, int size, Sort sort) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, sort);
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getMemberProfile(UUID memberId) {
        Member member = findMemberOrThrow(memberId);
        return MemberProfileResponse.from(member, memberAccountRepo.findByMember_Id(memberId));
    }

    @Transactional
    public MemberProfileResponse updateMemberContact(UUID memberId, MemberUpdateRequest request) {
        Member member = findMemberOrThrow(memberId);
        PhoneNumberRules.requireLocalFormat(request.getPhoneNumber());
        if (memberRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), memberId)) {
            throw new ConflictException("A member with this phone number already exists.");
        }

        // Field names only: the audit log records what changed, not the contact details themselves.
        List<String> changed = new ArrayList<>();
        if (!Objects.equals(member.getPhoneNumber(), request.getPhoneNumber())) changed.add("phoneNumber");
        if (!Objects.equals(member.getResidentialAddress(), request.getResidentialAddress())) changed.add("residentialAddress");
        if (!Objects.equals(member.getOccupation(), request.getOccupation())) changed.add("occupation");

        member.setPhoneNumber(request.getPhoneNumber());
        member.setResidentialAddress(request.getResidentialAddress());
        member.setOccupation(request.getOccupation());
        Member saved = memberRepository.save(member);
        if (!changed.isEmpty()) {
            auditLogService.record(AuditAction.MEMBER_CONTACT_UPDATED, AuditTargetType.MEMBER, memberId,
                    "Changed: " + String.join(", ", changed));
        }
        return MemberProfileResponse.from(saved, memberAccountRepo.findByMember_Id(memberId));
    }

    private Member findMemberOrThrow(UUID memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
    }

    // Every member gets a SHARES account automatically; the account chosen at registration is
    // the additional one, which can only be SAVINGS or CURRENT.
    private AccountType parseSelectedAccountType(String requested) {
        try {
            AccountType type = AccountType.valueOf(requested.trim().toUpperCase());
            if (type == AccountType.SAVINGS || type == AccountType.CURRENT) {
                return type;
            }
        } catch (IllegalArgumentException e) {
            // Unknown value: fall through to the same 400 as SHARES.
        }
        throw new BadRequestException("Account type must be SAVINGS or CURRENT");
    }

    private LocalDate parseDateOfBirth(String dateOfBirth) {
        try {
            return LocalDate.parse(dateOfBirth, DateTimeFormatter.ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new BadRequestException("Invalid date of birth; expected yyyy-MM-dd");
        }
    }
}
