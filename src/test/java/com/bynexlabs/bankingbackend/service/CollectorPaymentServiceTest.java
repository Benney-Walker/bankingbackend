package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorDaySummary;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorMemberResponse;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentResponse;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.CollectorPaymentType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorPaymentServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

    @Mock
    private CollectorLocationService locationService;

    @Mock
    private CollectorSubmissionService submissionService;

    @Mock
    private TransactionRepo transactionRepo;
    @Mock
    private TransactionService transactionService;
    @Mock
    private MemberAccountRepo memberAccountRepo;
    @Mock
    private CollectorPaymentApplier applier;

    private CollectorPaymentService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Staff collector = Staff.builder().id(UUID.randomUUID()).staffId("900110").branch(tarkwa).build();
    private final Member member = member(tarkwa);
    private final MemberAccount savings = account(member, "1000000002", AccountType.SAVINGS);
    private final MemberAccount shares = account(member, "1000000001", AccountType.SHARES);

    @BeforeEach
    void setUp() {
        service = new CollectorPaymentService(transactionRepo, transactionService, memberAccountRepo, applier, submissionService, locationService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        lenient().when(transactionService.record(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Member member(Branch branch) {
        return Member.builder().id(UUID.randomUUID()).firstName("Ama").lastName("Mensah").branch(branch)
                .membershipStatus(MembershipStatus.ACTIVE).build();
    }

    private static MemberAccount account(Member member, String number, AccountType type) {
        return MemberAccount.builder().id(UUID.randomUUID()).member(member).accountNumber(number).accountType(type)
                .status(AccountStatus.ACTIVE).currentBalance(BigDecimal.TEN).availableBalance(BigDecimal.TEN).build();
    }

    // The app sends a payment after its 5-minute window, with the phone's times.
    private static final LocalDateTime PHONE_CREATED = NOW.minusMinutes(6);
    private static final LocalDateTime PHONE_UPDATED = NOW.minusMinutes(5);

    private static CollectorPaymentRequest payment(CollectorPaymentType type, String accountNumber, String amount) {
        return CollectorPaymentRequest.builder().idempotencyKey("key-1").type(type).accountNumber(accountNumber)
                .amount(new BigDecimal(amount)).createdAt(PHONE_CREATED).updatedAt(PHONE_UPDATED).build();
    }

    private void accountExists(MemberAccount account) {
        when(memberAccountRepo.findWithMemberByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));
    }

    private void firstDepositMade(boolean made) {
        when(transactionRepo.existsByMemberAccount_Member_IdAndTransactionTypeAndStatus(
                member.getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED)).thenReturn(made);
    }

    private Transaction pending(MemberAccount account, String amount, LocalDateTime createdAt) {
        return Transaction.builder().id(UUID.randomUUID()).idempotencyKey("key-1")
                .transactionType(account.getAccountType() == AccountType.SHARES ? TransactionType.SHARES : TransactionType.DEPOSIT)
                .amount(new BigDecimal(amount)).memberAccount(account).initiatedBy(collector)
                .channel(TransactionChannel.COLLECTOR).status(TransactionStatus.PENDING).createdAt(createdAt).build();
    }

    // --- recording ------------------------------------------------------------------------

    // Owner decision 2026-11-23: final on arrival, with the phone's createdAt and updatedAt kept
    // and the time the backend received it.
    @Test
    void savingsDepositIsAppliedOnArrivalWithThePhonesTimes() {
        accountExists(savings);
        firstDepositMade(true);

        CollectorPaymentResponse response = service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector);

        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(saved.capture());
        Transaction transaction = saved.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getChannel()).isEqualTo(TransactionChannel.COLLECTOR);
        assertThat(transaction.getInitiatedBy()).isSameAs(collector);
        assertThat(transaction.getCreatedAt()).isEqualTo(PHONE_CREATED);
        assertThat(transaction.getUpdatedAt()).isEqualTo(PHONE_UPDATED);
        assertThat(transaction.getReceivedAt()).isEqualTo(NOW);
        verify(applier).apply(transaction);
        verify(submissionService).requireCreatedAfterLastSubmission(collector, PHONE_CREATED);
        verify(locationService).requireRecentLocation(collector, PHONE_CREATED);
        assertThat(response.createdAt()).isEqualTo(PHONE_CREATED);
        assertThat(response.receivedAt()).isEqualTo(NOW);
        assertThat(response.memberName()).isEqualTo("Ama Mensah");
    }

    @Test
    void sharePurchaseGoesToTheSharesAccount() {
        accountExists(shares);
        firstDepositMade(true);

        CollectorPaymentResponse response = service.record(payment(CollectorPaymentType.SHARES, "1000000001", "15"), collector);

        assertThat(response.type()).isEqualTo(CollectorPaymentType.SHARES);
        ArgumentCaptor<Transaction> saved = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(saved.capture());
        assertThat(saved.getValue().getTransactionType()).isEqualTo(TransactionType.SHARES);
    }

    @Test
    void typeMustMatchTheAccount() {
        accountExists(shares);

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000001", "15"), collector))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Savings deposits go to the member's savings account");
    }

    // Owner decision 2026-11-14: any member in the collector's own branch.
    @Test
    void memberInAnotherBranchIsRefused() {
        MemberAccount elsewhere = account(member(Branch.builder().id(UUID.randomUUID()).name("Bogoso").build()),
                "1000000009", AccountType.SAVINGS);
        accountExists(elsewhere);

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000009", "15"), collector))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("This member isn't in your branch");
        verify(transactionService, never()).record(any());
    }

    // Owner decision 2026-11-14: first deposits at the office only.
    @Test
    void memberWithoutAFirstDepositIsRefused() {
        accountExists(savings);
        firstDepositMade(false);

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "15"), collector))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member's first deposit must be made at the office");
    }

    @Test
    void createdAtInTheFutureIsRejected() {
        CollectorPaymentRequest request = payment(CollectorPaymentType.SAVINGS, "1000000002", "20");
        request.setCreatedAt(NOW.plusHours(1));
        request.setUpdatedAt(NOW.plusHours(1));

        assertThatThrownBy(() -> service.record(request, collector)).isInstanceOf(BadRequestException.class);
        verify(transactionService, never()).record(any());
    }

    @Test
    void updatedAtBeforeCreatedAtIsRejected() {
        CollectorPaymentRequest request = payment(CollectorPaymentType.SAVINGS, "1000000002", "20");
        request.setUpdatedAt(PHONE_CREATED.minusMinutes(1));

        assertThatThrownBy(() -> service.record(request, collector))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("updatedAt can't be before createdAt");
    }

    @Test
    void createdAtIsRequired() {
        CollectorPaymentRequest request = payment(CollectorPaymentType.SAVINGS, "1000000002", "20");
        request.setCreatedAt(null);

        assertThatThrownBy(() -> service.record(request, collector)).isInstanceOf(BadRequestException.class);
    }

    // Owner decision 2026-11-23: any age, as long as its day hasn't been submitted.
    @Test
    void paymentCreatedBeforeTheLastSubmissionIsRefused() {
        org.mockito.Mockito.doThrow(new ConflictException("This payment was recorded before your submission"))
                .when(submissionService).requireCreatedAfterLastSubmission(collector, PHONE_CREATED);

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector))
                .isInstanceOf(ConflictException.class);
        verify(transactionService, never()).record(any());
    }

    @Test
    void retryMatchingThePaymentAsItIsNowIsAccepted() {
        Transaction saved = pending(savings, "20", NOW.minusMinutes(2));
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(saved));

        CollectorPaymentResponse response = service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector);

        assertThat(response.amount()).isEqualByComparingTo("20");
        verify(transactionRepo).lockIdempotencyKey("key-1");
        verify(transactionService, never()).record(any());
    }

    // A key reused for another payment (different amount) must not swallow it.
    @Test
    void keyReusedForADifferentPaymentIsAConflict() {
        Transaction saved = pending(savings, "25", NOW.minusMinutes(2));
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This idempotency key was already used for a different transaction");
        verify(transactionService, never()).record(any());
    }

    @Test
    void keyReusedForAnotherMembersAccountIsAConflict() {
        Transaction saved = pending(savings, "20", NOW.minusMinutes(2));
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(saved));

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000099", "20"), collector))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void keyUsedByAnotherCollectorIsAConflict() {
        Transaction someoneElses = pending(savings, "25", NOW.minusMinutes(2));
        someoneElses.setInitiatedBy(Staff.builder().id(UUID.randomUUID()).build());
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(someoneElses));

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector))
                .isInstanceOf(ConflictException.class);
    }

    // Owner decision 2026-11-23: no location near the payment's time, no payment.
    @Test
    void paymentsAreRefusedWithoutALocationNearTheirTime() {
        org.mockito.Mockito.doThrow(new ConflictException("No location was recorded within 15 minutes of this payment"))
                .when(locationService).requireRecentLocation(eq(collector), any());

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("No location");
        verify(transactionService, never()).record(any());
    }

    // Owner decision 2026-11-15: submitting closes the day.
    @Test
    void paymentsAreRefusedOnceTheDayIsSubmitted() {
        org.mockito.Mockito.doThrow(new ConflictException("You've submitted your collections for the day."))
                .when(submissionService).requireCanRecord(collector);

        assertThatThrownBy(() -> service.record(payment(CollectorPaymentType.SAVINGS, "1000000002", "20"), collector))
                .isInstanceOf(ConflictException.class);
        verify(transactionService, never()).record(any());
    }

    // --- reads ----------------------------------------------------------------------------

    // Collectors see names and account numbers only (owner decision 2026-11-07).
    @Test
    void memberListGroupsAccountsByMember() {
        when(memberAccountRepo.findForBranch(any(), any(), any(), any())).thenReturn(List.of(shares, savings));

        List<CollectorMemberResponse> members = service.members(collector);

        assertThat(members).hasSize(1);
        assertThat(members.get(0).memberName()).isEqualTo("Ama Mensah");
        assertThat(members.get(0).accounts()).extracting(CollectorMemberResponse.Account::accountNumber)
                .containsExactly("1000000001", "1000000002");
    }

    @Test
    void daySummaryTotalsEverythingButFailedPayments() {
        Transaction deposit = pending(savings, "20", NOW.minusHours(2));
        deposit.setStatus(TransactionStatus.COMPLETED);
        Transaction share = pending(shares, "15", NOW.minusHours(1));
        Transaction failed = pending(savings, "100", NOW.minusMinutes(30));
        failed.setStatus(TransactionStatus.FAILED);
        when(transactionRepo.findByInitiatedBy_IdAndChannelAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                collector.getId(), TransactionChannel.COLLECTOR, LocalDate.of(2026, 11, 14).atStartOfDay(),
                LocalDate.of(2026, 11, 15).atStartOfDay())).thenReturn(List.of(failed, share, deposit));

        CollectorDaySummary summary = service.daySummary(collector, null);

        assertThat(summary.count()).isEqualTo(3);
        assertThat(summary.savingsTotal()).isEqualByComparingTo("20");
        assertThat(summary.sharesTotal()).isEqualByComparingTo("15");
        assertThat(summary.total()).isEqualByComparingTo("35");
    }
}
