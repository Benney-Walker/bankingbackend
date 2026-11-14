package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Member profile and contact updates (roadmap 2.3; search was removed by the owner on purpose). */
@ExtendWith(MockitoExtension.class)
class MemberManagementServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MemberAccountRepo memberAccountRepo;

    @Mock
    private BranchService branchService;

    private MemberService memberService;

    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, outboxService, memberAccountRepo, auditLogService, branchService);
    }

    private Member member() {
        Member member = new Member();
        member.setId(memberId);
        member.setFirstName("Ama");
        member.setLastName("Mensah");
        member.setDateOfBirth(LocalDate.of(1990, 5, 14));
        member.setPhoneNumber("0241234567");
        member.setResidentialAddress("Tarkwa");
        member.setOccupation("Trader");
        member.setMembershipStatus(MembershipStatus.ACTIVE);
        return member;
    }

    private MemberAccount account(String number, AccountType type) {
        MemberAccount account = new MemberAccount();
        account.setAccountNumber(number);
        account.setAccountType(type);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCurrentBalance(new BigDecimal("150.0000"));
        return account;
    }

    private MemberUpdateRequest update(String phone) {
        return MemberUpdateRequest.builder()
                .phoneNumber(phone)
                .residentialAddress("Takoradi")
                .occupation("Seamstress")
                .build();
    }

    @Test
    void profileIncludesAccountsButNoBalances() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member()));
        when(memberAccountRepo.findByMember_Id(memberId)).thenReturn(List.of(
                account("1004212345", AccountType.SHARES), account("1004312346", AccountType.SAVINGS)));

        MemberProfileResponse profile = memberService.getMemberProfile(memberId);

        assertThat(profile.getOccupation()).isEqualTo("Trader");
        assertThat(profile.getAccounts())
                .extracting(MemberProfileResponse.AccountSummary::getAccountNumber)
                .containsExactly("1004212345", "1004312346");
        assertThat(profile.getAccounts())
                .extracting(MemberProfileResponse.AccountSummary::getAccountType)
                .containsExactly(AccountType.SHARES, AccountType.SAVINGS);
    }

    @Test
    void unknownMemberIsNotFound() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> memberService.getMemberProfile(memberId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Member not found");
    }

    @Test
    void updateChangesOnlyPhoneAddressAndOccupation() {
        Member member = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberRepository.save(member)).thenReturn(member);
        when(memberAccountRepo.findByMember_Id(memberId)).thenReturn(List.of());

        MemberProfileResponse profile = memberService.updateMemberContact(memberId, update("0207654321"));

        assertThat(profile.getPhoneNumber()).isEqualTo("0207654321");
        assertThat(profile.getResidentialAddress()).isEqualTo("Takoradi");
        assertThat(profile.getOccupation()).isEqualTo("Seamstress");
        assertThat(member.getFirstName()).isEqualTo("Ama");
        assertThat(member.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 14));
        assertThat(member.getMembershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
        // Field names only, never the new values.
        verify(auditLogService).record(AuditAction.MEMBER_CONTACT_UPDATED, AuditTargetType.MEMBER, memberId,
                "Changed: phoneNumber, residentialAddress, occupation");
    }

    @Test
    void updateRejectsPhoneNotInLocalFormat() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member()));

        assertThatThrownBy(() -> memberService.updateMemberContact(memberId, update("+233207654321")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid phone number");
        verify(memberRepository, never()).save(any());
    }

    @Test
    void updateRejectsPhoneOfAnotherMember() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member()));
        when(memberRepository.existsByPhoneNumberAndIdNot("0207654321", memberId)).thenReturn(true);

        assertThatThrownBy(() -> memberService.updateMemberContact(memberId, update("0207654321")))
                .isInstanceOf(ConflictException.class);
        verify(memberRepository, never()).save(any());
    }
}
