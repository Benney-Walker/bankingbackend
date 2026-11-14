package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private BranchService branchService;

    private final Branch branch = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();

    @InjectMocks
    private MemberService memberService;

    private final Staff registrar = Staff.builder().id(UUID.randomUUID()).staffId("900105").build();

    private MemberDetails registration(String accountType) {
        return MemberDetails.builder()
                .firstName("Ama")
                .lastName("Mensah")
                .dob("1990-05-14")
                .phoneNumber("0241234567")
                .residentialAddress("Tarkwa")
                .occupation("Trader")
                .accountType(accountType)
                .branchId(branch.getId())
                .build();
    }

    @Test
    void registrationWithoutStaffIsUnauthorized() {
        assertThatThrownBy(() -> memberService.registerNewMember(registration("SAVINGS"), null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(memberRepository, outboxService, auditLogService);
    }

    @Test
    void rejectsDuplicatePhoneNumberWithoutSavingOrQueueingAccounts() {
        when(memberRepository.existsByPhoneNumber("0241234567")).thenReturn(true);

        assertThatThrownBy(() -> memberService.registerNewMember(registration("SAVINGS"), registrar))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A member with this phone number already exists.");
        verify(memberRepository, never()).save(any());
        verifyNoInteractions(outboxService);
    }

    // SHARES is created automatically for every member, so it can't be the selected account.
    @ParameterizedTest
    @ValueSource(strings = {"SHARES", "FIXED_DEPOSIT", "savingz"})
    void rejectsAccountTypeOtherThanSavingsOrCurrentBeforeAnyDatabaseWork(String accountType) {
        assertThatThrownBy(() -> memberService.registerNewMember(registration(accountType), registrar))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Account type must be SAVINGS or CURRENT");
        verifyNoInteractions(memberRepository, outboxService, auditLogService);
    }

    @Test
    void rejectsPhoneNumberNotInLocalFormat() {
        MemberDetails details = registration("SAVINGS");
        details.setPhoneNumber("+233241234567");

        assertThatThrownBy(() -> memberService.registerNewMember(details, registrar))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid phone number");
        verifyNoInteractions(memberRepository, outboxService, auditLogService);
    }

    @Test
    void rejectsBadlyFormattedDateOfBirth() {
        MemberDetails details = registration("CURRENT");
        details.setDob("14/05/1990");

        assertThatThrownBy(() -> memberService.registerNewMember(details, registrar))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid date of birth; expected yyyy-MM-dd");
        verifyNoInteractions(memberRepository, outboxService, auditLogService);
    }

    @Test
    void savesPendingMemberAndQueuesSharesAndSelectedAccountThroughTheOutbox() {
        UUID memberId = UUID.randomUUID();
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> {
            Member member = invocation.getArgument(0);
            member.setId(memberId);
            return member;
        });
        when(branchService.getBranchDetails(branch.getId())).thenReturn(branch);

        ResponseEntity<?> response = memberService.registerNewMember(registration("current"), registrar);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<?, ?>) response.getBody()).get("Id")).isEqualTo(memberId);

        ArgumentCaptor<Member> savedMember = ArgumentCaptor.forClass(Member.class);
        verify(memberRepository).save(savedMember.capture());
        assertThat(savedMember.getValue().getBranch()).isSameAs(branch);
        assertThat(savedMember.getValue().getMembershipStatus()).isEqualTo(MembershipStatus.PENDING);
        assertThat(savedMember.getValue().getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(savedMember.getValue().getRegisteredBy()).isSameAs(registrar);

        verify(outboxService).enqueue(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.DEFAULT_SHARE_ACCOUNT_ROUTING_KEY),
                eq(SharesAccountEvent.builder().memberId(memberId).build()));
        verify(outboxService).enqueue(
                eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.OTHER_ACCOUNT_CREATION_ROUTING_KEY),
                eq(OtherAccountCreationEvent.builder().memberId(memberId).accountType(AccountType.CURRENT).build()));
        verify(auditLogService).recordAs("900105", AuditAction.MEMBER_REGISTERED, AuditTargetType.MEMBER, memberId,
                "Selected account CURRENT");
    }
}
