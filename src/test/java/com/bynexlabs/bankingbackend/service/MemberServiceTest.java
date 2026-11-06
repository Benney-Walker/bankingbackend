package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.rabbitMQ.RabbitMQProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RabbitMQProducer rabbitMQProducer;

    @InjectMocks
    private MemberService memberService;

    private MemberDetails registration(String accountType) {
        return MemberDetails.builder()
                .firstName("Ama")
                .lastName("Mensah")
                .dob("1990-05-14")
                .phoneNumber("0241234567")
                .residentialAddress("Tarkwa")
                .occupation("Trader")
                .accountType(accountType)
                .build();
    }

    @Test
    void rejectsDuplicatePhoneNumberWithoutSavingOrQueueingAccounts() {
        when(memberRepository.existsByPhoneNumber("0241234567")).thenReturn(true);

        ResponseEntity<?> response = memberService.registerNewMember(registration("SAVINGS"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(rabbitMQProducer);
    }

    @Test
    void savesPendingMemberAndQueuesSharesAndSelectedAccount() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setId(memberId);
            return member;
        });

        ResponseEntity<?> response = memberService.registerNewMember(registration("savings"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("Id")).isEqualTo(memberId);

        ArgumentCaptor<Member> savedMember = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(savedMember.capture());
        assertThat(savedMember.getValue().getMembershipStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(savedMember.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 14));

        ArgumentCaptor<Object> sharesEvent = ArgumentCaptor.forClass(Object.class);
        verify(rabbitMQProducer).sendDefaultShareAccountEvent(sharesEvent.capture());
        assertThat(sharesEvent.getValue())
                .isEqualTo(SharesAccountEvent.builder().memberId(memberId).build());

        ArgumentCaptor<Object> otherEvent = ArgumentCaptor.forClass(Object.class);
        verify(rabbitMQProducer).sendOtherAccountCreationEvent(otherEvent.capture());
        assertThat(otherEvent.getValue())
                .isEqualTo(OtherAccountCreationEvent.builder()
                        .memberId(memberId)
                        .accountType(AccountType.SAVINGS)
                        .build());
    }
}
