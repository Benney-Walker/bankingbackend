package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorCorrectionRequest;
import com.bynexlabs.bankingbackend.DTO.collector.WithdrawalRequestCreate;
import com.bynexlabs.bankingbackend.DTO.collector.WithdrawalRequestResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionRequest;
import com.bynexlabs.bankingbackend.Repository.CollectorCorrectionRepo;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Repository.WithdrawalRequestRepo;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.CorrectionRecordType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.WithdrawalRequestStatus;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.CollectorCorrection;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.models.WithdrawalRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
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
class WithdrawalRequestServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

    @Mock
    private WithdrawalRequestRepo requestRepo;
    @Mock
    private MemberAccountRepo memberAccountRepo;
    @Mock
    private CollectorCorrectionRepo correctionRepo;
    @Mock
    private MoneyMovementService moneyMovementService;
    @Mock
    private TransactionRepo transactionRepo;

    private WithdrawalRequestService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Staff collector = Staff.builder().id(UUID.randomUUID()).staffId("900110").branch(tarkwa).build();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900105").branch(tarkwa).build();
    private final Branch takoradi = Branch.builder().id(UUID.randomUUID()).name("Takoradi").build();
    private final Staff otherBranchAgent = Staff.builder().id(UUID.randomUUID()).staffId("900106").branch(takoradi).build();
    private final Staff administrator = Staff.builder().id(UUID.randomUUID()).staffId("900101").branch(takoradi).build();
    private final Member member = Member.builder().id(UUID.randomUUID()).firstName("Ama").lastName("Mensah")
            .branch(tarkwa).membershipStatus(MembershipStatus.ACTIVE).build();
    private final MemberAccount savings = account("1000000002", AccountType.SAVINGS);

    @BeforeEach
    void setUp() {
        service = new WithdrawalRequestService(requestRepo, memberAccountRepo, correctionRepo, moneyMovementService, transactionRepo);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
    }

    private MemberAccount account(String number, AccountType type) {
        return MemberAccount.builder().id(UUID.randomUUID()).member(member).accountNumber(number).accountType(type)
                .status(AccountStatus.ACTIVE).currentBalance(BigDecimal.TEN).availableBalance(BigDecimal.TEN).build();
    }

    private WithdrawalRequest request(WithdrawalRequestStatus status, LocalDateTime createdAt) {
        return WithdrawalRequest.builder().id(UUID.randomUUID()).idempotencyKey("key-1").memberAccount(savings)
                .amount(new BigDecimal("100")).status(status).requestedBy(collector).createdAt(createdAt).build();
    }

    private void locked(WithdrawalRequest request) {
        when(requestRepo.lockById(request.getId())).thenReturn(Optional.of(request));
    }

    // --- collectors -----------------------------------------------------------------------

    // Creating a request never moves money.
    @Test
    void collectorCreatesAPendingRequestForABranchMember() {
        when(memberAccountRepo.findWithMemberByAccountNumber("1000000002")).thenReturn(Optional.of(savings));
        when(requestRepo.save(any(WithdrawalRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WithdrawalRequestResponse response = service.create(WithdrawalRequestCreate.builder()
                .idempotencyKey("key-1").accountNumber("1000000002").amount(new BigDecimal("100")).build(), collector);

        assertThat(response.status()).isEqualTo(WithdrawalRequestStatus.PENDING);
        assertThat(response.correctableUntil()).isEqualTo(NOW.plusMinutes(10));
        assertThat(response.requestedByStaffId()).isEqualTo("900110");
        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("10");
        verifyNoInteractions(moneyMovementService);
    }

    @Test
    void requestsAreOnlyForSavingsAccounts() {
        when(memberAccountRepo.findWithMemberByAccountNumber("1000000001"))
                .thenReturn(Optional.of(account("1000000001", AccountType.SHARES)));

        assertThatThrownBy(() -> service.create(WithdrawalRequestCreate.builder()
                .idempotencyKey("key-1").accountNumber("1000000001").amount(BigDecimal.TEN).build(), collector))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Withdrawal requests are only for savings accounts");
    }

    @Test
    void collectorCorrectsAmountWithinTenMinutes() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(5));
        locked(request);

        WithdrawalRequestResponse response = service.correct(request.getId(),
                CollectorCorrectionRequest.builder().amount(new BigDecimal("80")).build(), collector);

        assertThat(response.amount()).isEqualByComparingTo("80");
        ArgumentCaptor<CollectorCorrection> correction = ArgumentCaptor.forClass(CollectorCorrection.class);
        verify(correctionRepo).save(correction.capture());
        assertThat(correction.getValue().getRecordType()).isEqualTo(CorrectionRecordType.WITHDRAWAL_REQUEST);
        assertThat(correction.getValue().getPreviousAmount()).isEqualByComparingTo("100");
    }

    @Test
    void correctionAfterTheWindowIsRefused() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(11));
        locked(request);

        assertThatThrownBy(() -> service.correct(request.getId(),
                CollectorCorrectionRequest.builder().amount(BigDecimal.ONE).build(), collector))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void otherCollectorsCantCorrectARequest() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(1));
        locked(request);

        assertThatThrownBy(() -> service.correct(request.getId(),
                CollectorCorrectionRequest.builder().amount(BigDecimal.ONE).build(), agent))
                .isInstanceOf(ForbiddenException.class);
    }

    // --- office ---------------------------------------------------------------------------

    // While the collector can still correct it, the request can't be reviewed.
    @Test
    void requestCantBeApprovedDuringTheCorrectionWindow() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(3));
        locked(request);

        assertThatThrownBy(() -> service.approve(request.getId(), agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The collector can still correct this request until 10:07");
    }

    @Test
    void approvalRecordsTheReviewer() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(30));
        locked(request);

        WithdrawalRequestResponse response = service.approve(request.getId(), agent, false);

        assertThat(response.status()).isEqualTo(WithdrawalRequestStatus.APPROVED);
        assertThat(response.reviewedByStaffId()).isEqualTo("900105");
        assertThat(response.reviewedAt()).isEqualTo(NOW);
        verifyNoInteractions(moneyMovementService);
    }

    @Test
    void rejectionNeedsAReason() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), " ", agent, false))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectionKeepsTheReason() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(30));
        locked(request);

        WithdrawalRequestResponse response = service.reject(request.getId(), "Member asked to cancel", agent, false);

        assertThat(response.status()).isEqualTo(WithdrawalRequestStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Member asked to cancel");
    }

    // The member collects the cash at the office: paying records a normal withdrawal.
    @Test
    void payingRecordsTheWithdrawalAndLinksIt() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.APPROVED, NOW.minusHours(1));
        locked(request);
        Transaction withdrawal = Transaction.builder().id(UUID.randomUUID()).idempotencyKey("pay-1").build();
        when(transactionRepo.findByIdempotencyKey("pay-1")).thenReturn(Optional.of(withdrawal));

        WithdrawalRequestResponse response = service.pay(request.getId(), "pay-1", agent, false);

        ArgumentCaptor<TransactionRequest> sent = ArgumentCaptor.forClass(TransactionRequest.class);
        verify(moneyMovementService).withdraw(sent.capture(), eq(agent));
        assertThat(sent.getValue().getAccountNumber()).isEqualTo("1000000002");
        assertThat(sent.getValue().getAmount()).isEqualByComparingTo("100");
        assertThat(sent.getValue().getIdempotencyKey()).isEqualTo("pay-1");
        assertThat(response.status()).isEqualTo(WithdrawalRequestStatus.PAID);
        assertThat(response.transactionId()).isEqualTo(withdrawal.getId());
    }

    @Test
    void pendingRequestCantBePaid() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusHours(1));
        locked(request);

        assertThatThrownBy(() -> service.pay(request.getId(), "pay-1", agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only approved requests can be paid; this one is PENDING");
        verify(moneyMovementService, never()).withdraw(any(), any());
    }

    @Test
    void retriedPaymentReturnsThePaidRequest() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PAID, NOW.minusHours(1));
        request.setTransaction(Transaction.builder().id(UUID.randomUUID()).idempotencyKey("pay-1").build());
        locked(request);

        assertThat(service.pay(request.getId(), "pay-1", agent, false).status()).isEqualTo(WithdrawalRequestStatus.PAID);
        verify(moneyMovementService, never()).withdraw(any(), any());
    }

    // --- branch rule (owner decision 2026-11-14) ------------------------------------------

    @Test
    void agentListsOnlyTheirOwnBranchsRequests() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(30));
        Page<WithdrawalRequest> page = new PageImpl<>(List.of(request));
        when(requestRepo.findByStatusAndMemberAccount_Member_Branch_Id(eq(WithdrawalRequestStatus.PENDING), eq(tarkwa.getId()),
                any(Pageable.class))).thenReturn(page);

        assertThat(service.list(null, 0, 20, agent, false).getContent()).hasSize(1);
        verify(requestRepo, never()).findByStatus(any(), any());
    }

    @Test
    void administratorListsEveryBranch() {
        when(requestRepo.findByStatus(eq(WithdrawalRequestStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(WithdrawalRequestStatus.APPROVED, 0, 20, administrator, true);

        verify(requestRepo, never()).findByStatusAndMemberAccount_Member_Branch_Id(any(), any(), any());
    }

    @Test
    void agentCantApproveAnotherBranchsRequest() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(30));
        locked(request);

        assertThatThrownBy(() -> service.approve(request.getId(), otherBranchAgent, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("This request is from another branch");
        assertThat(request.getStatus()).isEqualTo(WithdrawalRequestStatus.PENDING);
    }

    @Test
    void agentCantRejectAnotherBranchsRequest() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(30));
        locked(request);

        assertThatThrownBy(() -> service.reject(request.getId(), "Member asked to cancel", otherBranchAgent, false))
                .isInstanceOf(ForbiddenException.class);
        assertThat(request.getStatus()).isEqualTo(WithdrawalRequestStatus.PENDING);
    }

    @Test
    void agentCantPayAnotherBranchsRequest() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.APPROVED, NOW.minusHours(1));
        locked(request);

        assertThatThrownBy(() -> service.pay(request.getId(), "pay-1", otherBranchAgent, false))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(moneyMovementService);
    }

    @Test
    void administratorCanApproveAnyBranchsRequest() {
        WithdrawalRequest request = request(WithdrawalRequestStatus.PENDING, NOW.minusMinutes(30));
        locked(request);

        assertThat(service.approve(request.getId(), administrator, true).status())
                .isEqualTo(WithdrawalRequestStatus.APPROVED);
    }
}
