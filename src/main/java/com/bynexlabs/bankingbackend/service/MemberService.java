package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.rabbitMQ.RabbitMQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;

    private final RabbitMQProducer rabbitMQProducer;

    public Member getMemberDetails(UUID memberId) {
        return memberRepository.findById(memberId).orElse(null);
    }

    public ResponseEntity<?> registerNewMember(MemberDetails request) {

        if (memberRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "status", "failed",
                    "message", "A member with this phone number already exists."
            ));
        }

        LocalDate dob = LocalDate.parse(request.getDob(), DateTimeFormatter.ISO_DATE);

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
        rabbitMQProducer.sendDefaultShareAccountEvent(sharesAccountEvent);

        OtherAccountCreationEvent otherEvent = OtherAccountCreationEvent.builder()
                .memberId(savedMember.getId())
                .accountType(AccountType.valueOf(request.getAccountType().toUpperCase()))
                .build();
        rabbitMQProducer.sendOtherAccountCreationEvent(otherEvent);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "Id", savedMember.getId()
        ));
    }
}
