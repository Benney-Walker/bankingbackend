package com.bynexlabs.bankingbackend.service;

import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageImpl;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.DTO.member.MemberSummaryResponse;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Member profile, contact updates (roadmap 2.3) and member search (owner decisions 2026-11-23). */
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

    // --- search (owner decisions 2026-11-23) ------------------------------------------------

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch takoradi = Branch.builder().id(UUID.randomUUID()).name("Takoradi").build();

    private Staff staffAt(Branch branch) {
        return Staff.builder().id(UUID.randomUUID()).staffId("900105").branch(branch).build();
    }

    private Member memberAt(Branch branch) {
        Member member = member();
        member.setBranch(branch);
        return member;
    }

    private MemberAccount accountOf(Member member, String number, AccountType type) {
        MemberAccount account = account(number, type);
        account.setMember(member);
        return account;
    }

    private void searchFinds(String query, Member member, MemberAccount... accounts) {
        when(memberRepository.search(eq(query), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(member)));
        when(memberAccountRepo.findByMember_IdIn(List.of(member.getId()))).thenReturn(List.of(accounts));
    }

    @Test
    void searchShowsNameBranchStatusAndAccountsWithoutBalances() {
        Member member = memberAt(tarkwa);
        searchFinds("ama", member, accountOf(member, "1004312346", AccountType.SAVINGS));

        MemberSummaryResponse row = memberService.searchMembers(" ama ", 0, 20, staffAt(tarkwa), false)
                .getContent().get(0);

        assertThat(row.getFirstName()).isEqualTo("Ama");
        assertThat(row.getBranchName()).isEqualTo("Tarkwa");
        assertThat(row.getBranchId()).isEqualTo(tarkwa.getId());
        assertThat(row.getMembershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(row.getAccounts()).extracting(MemberProfileResponse.AccountSummary::getAccountNumber)
                .containsExactly("1004312346");
    }

    @Test
    void agentSeesThePhoneOfTheirOwnBranchsMember() {
        Member member = memberAt(tarkwa);
        searchFinds("0241", member);

        assertThat(memberService.searchMembers("0241", 0, 20, staffAt(tarkwa), false).getContent().get(0)
                .getPhoneNumber()).isEqualTo("0241234567");
    }

    // An agent finds members of every branch, but not another branch's member's phone.
    @Test
    void agentDoesntSeeThePhoneOfAnotherBranchsMember() {
        Member member = memberAt(tarkwa);
        searchFinds("1004312346", member, accountOf(member, "1004312346", AccountType.SAVINGS));

        MemberSummaryResponse row = memberService.searchMembers("1004312346", 0, 20, staffAt(takoradi), false)
                .getContent().get(0);

        assertThat(row.getPhoneNumber()).isNull();
        assertThat(row.getBranchName()).isEqualTo("Tarkwa");
        assertThat(row.getAccounts()).hasSize(1);
    }

    @Test
    void administratorSeesEveryPhone() {
        Member member = memberAt(tarkwa);
        searchFinds("ama", member);

        assertThat(memberService.searchMembers("ama", 0, 20, staffAt(takoradi), true).getContent().get(0)
                .getPhoneNumber()).isEqualTo("0241234567");
    }

    @Test
    void searchNeedsAQuery() {
        assertThatThrownBy(() -> memberService.searchMembers("  ", 0, 20, staffAt(tarkwa), false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Enter a name, phone number or account number to search");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void pageSizeIsCapped() {
        assertThatThrownBy(() -> memberService.searchMembers("ama", 0, 101, staffAt(tarkwa), false))
                .isInstanceOf(BadRequestException.class);
    }

    // --- member list (owner decisions 2026-11-23, increment #4) -----------------------------

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    @Test
    void agentListsOnlyTheirOwnBranchNewestFirstWithPhones() {
        Member member = memberAt(tarkwa);
        when(memberRepository.findByBranch_Id(tarkwa.getId(), PageRequest.of(0, 20, NEWEST_FIRST)))
                .thenReturn(new PageImpl<>(List.of(member)));

        MemberSummaryResponse row = memberService.listMembers(null, null, 0, 20, staffAt(tarkwa), false)
                .getContent().get(0);

        assertThat(row.getPhoneNumber()).isEqualTo("0241234567");
        verify(memberRepository, never()).findAll(any(Pageable.class));
    }

    @Test
    void agentCantListAnotherBranch() {
        assertThatThrownBy(() -> memberService.listMembers(null, takoradi.getId(), 0, 20, staffAt(tarkwa), false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only list members of your own branch");
        verifyNoInteractions(memberRepository);
    }

    @Test
    void agentFiltersTheirBranchByStatus() {
        when(memberRepository.findByBranch_IdAndMembershipStatus(tarkwa.getId(), MembershipStatus.PENDING,
                PageRequest.of(0, 20, NEWEST_FIRST))).thenReturn(new PageImpl<>(List.of()));

        memberService.listMembers(MembershipStatus.PENDING, tarkwa.getId(), 0, 20, staffAt(tarkwa), false);
    }

    @Test
    void administratorListsEveryBranch() {
        when(memberRepository.findAll(PageRequest.of(0, 20, NEWEST_FIRST))).thenReturn(new PageImpl<>(List.of(memberAt(tarkwa))));

        assertThat(memberService.listMembers(null, null, 0, 20, staffAt(takoradi), true).getContent()).hasSize(1);
    }

    @Test
    void administratorNarrowsToOneBranchAndStatus() {
        when(memberRepository.findByMembershipStatus(MembershipStatus.ACTIVE, PageRequest.of(1, 50, NEWEST_FIRST)))
                .thenReturn(new PageImpl<>(List.of()));
        when(memberRepository.findByBranch_Id(tarkwa.getId(), PageRequest.of(0, 20, NEWEST_FIRST)))
                .thenReturn(new PageImpl<>(List.of()));

        memberService.listMembers(MembershipStatus.ACTIVE, null, 1, 50, staffAt(takoradi), true);
        memberService.listMembers(null, tarkwa.getId(), 0, 20, staffAt(takoradi), true);
    }
}
