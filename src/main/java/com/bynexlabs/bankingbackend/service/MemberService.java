package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
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

    public Member getMemberDetails(UUID memberId) {
        return memberRepository.findById(memberId).orElse(null);
    }

    /**
     * Saves the member (PENDING) and both account-creation messages in one transaction via the
     * outbox: either all three are stored or none are, so a member can never end up without
     * accounts because RabbitMQ was unavailable at the time.
     */
    @Transactional
    public ResponseEntity<?> registerNewMember(MemberDetails request) {
        // Validate the input before touching the database.
        AccountType accountType = parseSelectedAccountType(request.getAccountType());
        LocalDate dob = parseDateOfBirth(request.getDob());

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
