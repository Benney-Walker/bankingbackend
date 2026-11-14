package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.transaction.TransactionRequest;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransferRequest;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.FinancialSettings;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.service.PostingService.Posting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoneyMovementServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

    @Mock
    private AccountLockService accountLockService;
    @Mock
    private TransactionService transactionService;
    @Mock
    private TransactionRepo transactionRepo;
    @Mock
    private MemberAccountRepo memberAccountRepo;
    @Mock
    private FinancialSettingsService settingsService;
    @Mock
    private PostingService postingService;
    @Mock
    private TransactionSmsService transactionSmsService;

    private MoneyMovementService service;

    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900105").build();

    @BeforeEach
    void setUp() {
        service = new MoneyMovementService(accountLockService, transactionService, transactionRepo,
                memberAccountRepo, settingsService, postingService, transactionSmsService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        lenient().when(transactionService.findReplay(anyString(), any(), anyString(), any(BigDecimal.class)))
                .thenReturn(Optional.empty());
        lenient().when(transactionService.findReplay(anyString(), any(), anyString(), anyString(), any(BigDecimal.class)))
                .thenReturn(Optional.empty());
        lenient().when(transactionService.record(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** The mock values seeded by V31 and V32. */
    private static FinancialSettings settings() {
        return FinancialSettings.builder()
                .initialShareAmount(new BigDecimal("50"))
                .minFirstDeposit(new BigDecimal("100"))
                .withdrawalMinBalance(new BigDecimal("20"))
                .withdrawalDailyLimit(new BigDecimal("10000"))
                .withdrawalFeeEnabled(true)
                .withdrawalFeeType(WithdrawalFeeType.FLAT)
                .withdrawalFeeValue(new BigDecimal("2"))
                .build();
    }

    private static Member member() {
        return Member.builder().id(UUID.randomUUID()).firstName("Ama").lastName("Mensah")
                .membershipStatus(MembershipStatus.ACTIVE).build();
    }

    private static MemberAccount account(Member member, String number, AccountType type, String balance) {
        return MemberAccount.builder().id(UUID.randomUUID()).member(member).accountNumber(number).accountType(type)
                .status(AccountStatus.ACTIVE)
                .currentBalance(new BigDecimal(balance)).availableBalance(new BigDecimal(balance)).build();
    }

    private static TransactionRequest request(String accountNumber, String amount) {
        return TransactionRequest.builder().idempotencyKey("key-1").accountNumber(accountNumber)
                .amount(new BigDecimal(amount)).description("Counter deposit").build();
    }

    private void firstDepositMade(Member member, boolean made) {
        when(transactionRepo.existsByMemberAccount_Member_IdAndTransactionTypeAndStatus(
                member.getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED)).thenReturn(made);
    }

    private Posting posted() {
        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(postingService).post(any(Transaction.class), posting.capture(), eq(NOW));
        return posting.getValue();
    }

    private Transaction recorded() {
        ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(transaction.capture());
        return transaction.getValue();
    }

    private static void assertBalance(MemberAccount account, String expected) {
        assertThat(account.getCurrentBalance()).isEqualByComparingTo(expected);
        assertThat(account.getAvailableBalance()).isEqualByComparingTo(expected);
    }

    // --- deposits -------------------------------------------------------------------------

    @Test
    void depositCreditsTheAccountAndPostsADeposit() {
        Member member = member();
        MemberAccount savings = account(member, "1000000002", AccountType.SAVINGS, "100");
        MemberAccount shares = account(member, "1000000001", AccountType.SHARES, "50");
        when(accountLockService.lockWithSharesAccount("1000000002"))
                .thenReturn(new AccountLockService.AccountWithShares(savings, shares));
        firstDepositMade(member, true);

        TransactionResponse response = service.deposit(request("1000000002", "40"), agent);

        assertBalance(savings, "140");
        assertBalance(shares, "50");
        Transaction transaction = recorded();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getInitiatedBy()).isSameAs(agent);
        assertThat(transaction.getCreatedAt()).isEqualTo(NOW);
        assertThat(response.getAmount()).isEqualByComparingTo("40");

        Posting posting = posted();
        assertThat(posting.event()).isEqualTo(PostingEvent.DEPOSIT);
        assertThat(posting.primaryType()).isEqualTo(AccountType.SAVINGS);
        assertThat(posting.amounts()).containsEntry(AmountPart.PRINCIPAL, new BigDecimal("40"));
        // Owner decision 2026-11-14: every transaction texts the member their new balance.
        verify(transactionSmsService).deposit(transaction, savings);
    }

    // One split entry (owner decision 2026-11-14): the share amount goes to the shares account.
    @Test
    void firstDepositSplitsTheShareAmountIntoTheSharesAccount() {
        Member member = member();
        MemberAccount current = account(member, "1000000002", AccountType.CURRENT, "0");
        MemberAccount shares = account(member, "1000000001", AccountType.SHARES, "0");
        when(accountLockService.lockWithSharesAccount("1000000002"))
                .thenReturn(new AccountLockService.AccountWithShares(current, shares));
        firstDepositMade(member, false);
        when(settingsService.current()).thenReturn(settings());

        service.deposit(request("1000000002", "120"), agent);

        assertBalance(current, "70");
        assertBalance(shares, "50");
        Transaction transaction = recorded();
        assertThat(transaction.getAmount()).isEqualByComparingTo("120");
        assertThat(transaction.getShareAmount()).isEqualByComparingTo("50");
        assertThat(transaction.getCounterpartyAccount()).isSameAs(shares);

        Posting posting = posted();
        assertThat(posting.event()).isEqualTo(PostingEvent.FIRST_DEPOSIT);
        assertThat(posting.primaryType()).isEqualTo(AccountType.CURRENT);
        assertThat(posting.amounts())
                .containsEntry(AmountPart.PRINCIPAL, new BigDecimal("120"))
                .containsEntry(AmountPart.SHARE, new BigDecimal("50"))
                .containsEntry(AmountPart.REMAINDER, new BigDecimal("70"));
        verify(transactionSmsService).firstDeposit(transaction, current, shares);
    }

    @Test
    void firstDepositBelowTheMinimumIsRejected() {
        Member member = member();
        MemberAccount savings = account(member, "1000000002", AccountType.SAVINGS, "0");
        when(accountLockService.lockWithSharesAccount("1000000002"))
                .thenReturn(new AccountLockService.AccountWithShares(savings, account(member, "1000000001", AccountType.SHARES, "0")));
        firstDepositMade(member, false);
        when(settingsService.current()).thenReturn(settings());

        assertThatThrownBy(() -> service.deposit(request("1000000002", "99.99"), agent))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The first deposit must be at least GHS 100.00");
        assertBalance(savings, "0");
        verify(transactionService, never()).record(any());
        verifyNoInteractions(transactionSmsService);
    }

    @Test
    void depositIntoTheSharesAccountIsRejected() {
        Member member = member();
        MemberAccount shares = account(member, "1000000001", AccountType.SHARES, "50");
        when(accountLockService.lockWithSharesAccount("1000000001"))
                .thenReturn(new AccountLockService.AccountWithShares(shares, shares));

        assertThatThrownBy(() -> service.deposit(request("1000000001", "40"), agent))
                .isInstanceOf(BadRequestException.class);
        verify(transactionService, never()).record(any());
    }

    // A retry with the same key returns the original result and moves no money.
    @Test
    void retriedDepositReturnsTheRecordedTransaction() {
        Member member = member();
        MemberAccount savings = account(member, "1000000002", AccountType.SAVINGS, "140");
        when(accountLockService.lockWithSharesAccount("1000000002"))
                .thenReturn(new AccountLockService.AccountWithShares(savings, account(member, "1000000001", AccountType.SHARES, "50")));
        Transaction original = Transaction.builder().idempotencyKey("key-1").transactionType(TransactionType.DEPOSIT)
                .amount(new BigDecimal("40")).memberAccount(savings).status(TransactionStatus.COMPLETED).build();
        when(transactionService.findReplay("key-1", TransactionType.DEPOSIT, "1000000002", new BigDecimal("40")))
                .thenReturn(Optional.of(original));

        TransactionResponse response = service.deposit(request("1000000002", "40"), agent);

        assertThat(response.getIdempotencyKey()).isEqualTo("key-1");
        assertBalance(savings, "140");
        verify(transactionService, never()).record(any());
        verify(postingService, never()).post(any(), any(), any());
        // The member was texted when the transaction was first recorded.
        verifyNoInteractions(transactionSmsService);
    }

    // --- share purchases ------------------------------------------------------------------

    @Test
    void sharePurchaseCreditsTheSharesAccount() {
        Member member = member();
        MemberAccount shares = account(member, "1000000001", AccountType.SHARES, "50");
        when(accountLockService.lockWithSharesAccount("1000000001"))
                .thenReturn(new AccountLockService.AccountWithShares(shares, shares));
        firstDepositMade(member, true);

        service.purchaseShares(request("1000000001", "25"), agent);

        assertBalance(shares, "75");
        Transaction transaction = recorded();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.SHARES);
        assertThat(posted().event()).isEqualTo(PostingEvent.SHARE_PURCHASE);
        verify(transactionSmsService).sharePurchase(transaction, shares);
    }

    // Confirmed rule: a member's first transaction is always a deposit.
    @Test
    void sharePurchaseBeforeTheFirstDepositIsRejected() {
        Member member = member();
        MemberAccount shares = account(member, "1000000001", AccountType.SHARES, "0");
        when(accountLockService.lockWithSharesAccount("1000000001"))
                .thenReturn(new AccountLockService.AccountWithShares(shares, shares));
        firstDepositMade(member, false);

        assertThatThrownBy(() -> service.purchaseShares(request("1000000001", "25"), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member's first transaction must be a deposit");
    }

    @Test
    void sharePurchaseIntoASavingsAccountIsRejected() {
        Member member = member();
        MemberAccount savings = account(member, "1000000002", AccountType.SAVINGS, "100");
        when(accountLockService.lockWithSharesAccount("1000000002"))
                .thenReturn(new AccountLockService.AccountWithShares(savings, account(member, "1000000001", AccountType.SHARES, "50")));

        assertThatThrownBy(() -> service.purchaseShares(request("1000000002", "25"), agent))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Share purchases go to the member's shares account");
    }

    // --- withdrawals ----------------------------------------------------------------------

    private void noWithdrawalsToday(MemberAccount account, String alreadyWithdrawn) {
        when(transactionRepo.sumAmounts(account.getId(), TransactionType.WITHDRAWAL, TransactionStatus.COMPLETED,
                NOW.toLocalDate().atStartOfDay(), NOW.toLocalDate().plusDays(1).atStartOfDay()))
                .thenReturn(new BigDecimal(alreadyWithdrawn));
    }

    // Owner decision 2026-11-14: the fee is taken on top of the amount.
    @Test
    void withdrawalTakesTheFeeOnTopOfTheAmount() {
        MemberAccount savings = account(member(), "1000000002", AccountType.SAVINGS, "500");
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(savings);
        when(settingsService.current()).thenReturn(settings());
        noWithdrawalsToday(savings, "0");

        service.withdraw(request("1000000002", "100"), agent);

        assertBalance(savings, "398");
        Transaction transaction = recorded();
        assertThat(transaction.getAmount()).isEqualByComparingTo("100");
        assertThat(transaction.getFeeAmount()).isEqualByComparingTo("2");
        Posting posting = posted();
        assertThat(posting.event()).isEqualTo(PostingEvent.WITHDRAWAL);
        assertThat(posting.amounts())
                .containsEntry(AmountPart.PRINCIPAL, new BigDecimal("100"))
                .containsEntry(AmountPart.FEE, new BigDecimal("2"));
        verify(transactionSmsService).withdrawal(transaction, savings);
    }

    @Test
    void percentageFeeIsRoundedToTwoDecimalPlaces() {
        FinancialSettings settings = settings();
        settings.setWithdrawalFeeType(WithdrawalFeeType.PERCENTAGE);
        settings.setWithdrawalFeeValue(new BigDecimal("1.5"));
        MemberAccount current = account(member(), "1000000002", AccountType.CURRENT, "1000");
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(current);
        when(settingsService.current()).thenReturn(settings);
        noWithdrawalsToday(current, "0");

        service.withdraw(request("1000000002", "333.33"), agent);

        assertThat(recorded().getFeeAmount()).isEqualTo(new BigDecimal("5.00"));
        assertBalance(current, "661.67");
    }

    @Test
    void withdrawalMustLeaveTheMinimumBalanceAfterTheFee() {
        MemberAccount savings = account(member(), "1000000002", AccountType.SAVINGS, "121.99");
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(savings);
        when(settingsService.current()).thenReturn(settings());

        assertThatThrownBy(() -> service.withdraw(request("1000000002", "100"), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("must keep at least GHS 20.00")
                .hasMessageContaining("The most that can be withdrawn now is GHS 99.99");
        assertBalance(savings, "121.99");
        verify(transactionService, never()).record(any());
        verifyNoInteractions(transactionSmsService);
    }

    @Test
    void withdrawalOverTheDailyLimitIsRejected() {
        MemberAccount savings = account(member(), "1000000002", AccountType.SAVINGS, "50000");
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(savings);
        when(settingsService.current()).thenReturn(settings());
        noWithdrawalsToday(savings, "9950");

        assertThatThrownBy(() -> service.withdraw(request("1000000002", "100"), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("daily withdrawal limit of GHS 10000.00")
                .hasMessageContaining("GHS 50.00 can still be withdrawn");
        verify(transactionService, never()).record(any());
    }

    @Test
    void noFeeWhenTheFeeIsSwitchedOff() {
        FinancialSettings settings = settings();
        settings.setWithdrawalFeeEnabled(false);
        MemberAccount savings = account(member(), "1000000002", AccountType.SAVINGS, "500");
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(savings);
        when(settingsService.current()).thenReturn(settings);
        noWithdrawalsToday(savings, "0");

        service.withdraw(request("1000000002", "100"), agent);

        assertBalance(savings, "400");
        assertThat(recorded().getFeeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawalFromTheSharesAccountIsRejected() {
        MemberAccount shares = account(member(), "1000000001", AccountType.SHARES, "500");
        when(accountLockService.lockForTransaction("1000000001")).thenReturn(shares);

        assertThatThrownBy(() -> service.withdraw(request("1000000001", "100"), agent))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Withdrawals are only from savings or current accounts");
    }

    // --- transfers ------------------------------------------------------------------------

    private static TransferRequest transfer(String from, String to, String amount) {
        return TransferRequest.builder().idempotencyKey("key-2").fromAccountNumber(from).toAccountNumber(to)
                .amount(new BigDecimal(amount)).build();
    }

    // Owner decision 2026-11-14: transfers are allowed between members too.
    @Test
    void transferBetweenMembersMovesTheMoneyAndPosts() {
        MemberAccount from = account(member(), "1000000002", AccountType.SAVINGS, "500");
        Member receiver = member();
        MemberAccount to = account(receiver, "1000000005", AccountType.CURRENT, "0");
        when(accountLockService.lockForTransfer("1000000002", "1000000005")).thenReturn(List.of(from, to));
        firstDepositMade(receiver, true);
        when(settingsService.current()).thenReturn(settings());

        service.transfer(transfer("1000000002", "1000000005", "30"), agent);

        assertBalance(from, "470");
        assertBalance(to, "30");
        Transaction transaction = recorded();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.TRANSFER);
        assertThat(transaction.getMemberAccount()).isSameAs(from);
        assertThat(transaction.getCounterpartyAccount()).isSameAs(to);
        assertThat(transaction.getFeeAmount()).isEqualByComparingTo("0");
        Posting posting = posted();
        assertThat(posting.event()).isEqualTo(PostingEvent.TRANSFER);
        assertThat(posting.primaryType()).isEqualTo(AccountType.SAVINGS);
        assertThat(posting.counterpartyType()).isEqualTo(AccountType.CURRENT);
        verify(transactionSmsService).transfer(transaction, from, to);
    }

    @Test
    void transferToAMemberWithoutAFirstDepositIsRejected() {
        MemberAccount from = account(member(), "1000000002", AccountType.SAVINGS, "500");
        Member receiver = member();
        MemberAccount to = account(receiver, "1000000005", AccountType.SAVINGS, "0");
        when(accountLockService.lockForTransfer("1000000002", "1000000005")).thenReturn(List.of(from, to));
        firstDepositMade(receiver, false);

        assertThatThrownBy(() -> service.transfer(transfer("1000000002", "1000000005", "30"), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The receiving member's first transaction must be a deposit");
        assertBalance(from, "500");
    }

    @Test
    void transferMustLeaveTheMinimumBalance() {
        Member member = member();
        MemberAccount from = account(member, "1000000002", AccountType.SAVINGS, "40");
        MemberAccount to = account(member, "1000000003", AccountType.CURRENT, "0");
        when(accountLockService.lockForTransfer("1000000002", "1000000003")).thenReturn(List.of(from, to));
        firstDepositMade(member, true);
        when(settingsService.current()).thenReturn(settings());

        assertThatThrownBy(() -> service.transfer(transfer("1000000002", "1000000003", "30"), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("The most that can be transferred now is GHS 20.00");
        verify(transactionService, never()).record(any());
    }

    @Test
    void transferInvolvingASharesAccountIsRejected() {
        Member member = member();
        MemberAccount shares = account(member, "1000000001", AccountType.SHARES, "500");
        MemberAccount savings = account(member, "1000000002", AccountType.SAVINGS, "0");
        when(accountLockService.lockForTransfer("1000000001", "1000000002")).thenReturn(List.of(shares, savings));

        assertThatThrownBy(() -> service.transfer(transfer("1000000001", "1000000002", "30"), agent))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Transfers are only between savings and current accounts");
    }

    // --- reads ----------------------------------------------------------------------------

    @Test
    void historyRejectsAnOversizedPage() {
        assertThatThrownBy(() -> service.history("1000000002", 0, MoneyMovementService.MAX_PAGE_SIZE + 1))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void accountShowsBalancesAndOwner() {
        MemberAccount savings = account(member(), "1000000002", AccountType.SAVINGS, "250");
        when(memberAccountRepo.findWithMemberByAccountNumber("1000000002")).thenReturn(Optional.of(savings));

        assertThat(service.getAccount(" 1000000002 ").memberName()).isEqualTo("Ama Mensah");
    }
}
