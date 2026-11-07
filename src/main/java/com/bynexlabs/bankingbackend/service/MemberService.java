package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberSummaryResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.PhoneNumberRules;
import com.bynexlabs.bankingbackend.Utility.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.Utility.UnauthorizedException;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
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
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    private final OutboxService outboxService;

    private final MemberAccountRepo memberAccountRepo;

    // Upper bound on the search page size, so one request can't pull the whole table.
    static final int MAX_PAGE_SIZE = 100;

    public Member getMemberDetails(UUID memberId) {
        return memberRepository.findById(memberId).orElse(null);
    }

    /**
     * Saves the member (PENDING) and both account-creation messages in one transaction via the
     * outbox: either all three are stored or none are, so a member can never end up without
     * accounts because RabbitMQ was unavailable at the time.
     */
    @Transactional
    public ResponseEntity<?> registerNewMember(MemberDetails request, Staff registeredBy) {
        if (registeredBy == null) {
            throw new UnauthorizedException("Staff account not found or inactive");
        }
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
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "Id", savedMember.getId()
        ));
    }

    /** Search by name, phone or account number; a blank query lists all members. */
    @Transactional(readOnly = true)
    public PagedModel<MemberSummaryResponse> searchMembers(String query, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("lastName", "firstName"));
        Page<Member> members = (query == null || query.isBlank())
                ? memberRepository.findAll(pageRequest)
                : memberRepository.search(query.trim(), pageRequest);
        return new PagedModel<>(members.map(MemberSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public MemberProfileResponse getMemberProfile(UUID memberId) {
        Member member = findMemberOrThrow(memberId);
        return MemberProfileResponse.from(member, memberAccountRepo.findByMember_Id(memberId));
    }

    /**
     * Owner rule (2026-11-07): after registration only the phone number, residential address and
     * occupation can change. Everything else, including names and date of birth, is locked.
     */
    @Transactional
    public MemberProfileResponse updateMemberContact(UUID memberId, MemberUpdateRequest request) {
        Member member = findMemberOrThrow(memberId);
        PhoneNumberRules.requireLocalFormat(request.getPhoneNumber());
        if (memberRepository.existsByPhoneNumberAndIdNot(request.getPhoneNumber(), memberId)) {
            throw new ConflictException("A member with this phone number already exists.");
        }

        member.setPhoneNumber(request.getPhoneNumber());
        member.setResidentialAddress(request.getResidentialAddress());
        member.setOccupation(request.getOccupation());
        Member saved = memberRepository.save(member);
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
