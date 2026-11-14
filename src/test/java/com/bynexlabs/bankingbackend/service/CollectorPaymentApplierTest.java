package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectorPaymentApplierTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 11);

    @Mock
    private TransactionRepo transactionRepo;
    @Mock
    private AccountLockService accountLockService;
    @Mock
    private PostingService postingService;
    @Mock
    private TransactionSmsService transactionSmsService;

    private CollectorPaymentApplier applier;

    @BeforeEach
    void setUp() {
        applier = new CollectorPaymentApplier(transactionRepo, accountLockService, postingService, transactionSmsService);
        applier.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
    }

    private static MemberAccount account(AccountType type, String balance) {
        return MemberAccount.builder().id(UUID.randomUUID()).member(Member.builder().id(UUID.randomUUID()).build())
                .accountNumber(type == AccountType.SHARES ? "1000000001" : "1000000002").accountType(type)
                .status(AccountStatus.ACTIVE).currentBalance(new BigDecimal(balance)).availableBalance(new BigDecimal(balance)).build();
    }

    private static Transaction pending(MemberAccount account, TransactionType type, String amount) {
        return Transaction.builder().id(UUID.randomUUID()).transactionType(type).amount(new BigDecimal(amount))
                .memberAccount(account).channel(TransactionChannel.COLLECTOR).status(TransactionStatus.PENDING)
                .createdAt(NOW.minusMinutes(11)).build();
    }

    @Test
    void depositIsAppliedPostedAgainstCollectorCashAndTexted() {
        MemberAccount savings = account(AccountType.SAVINGS, "100");
        Transaction transaction = pending(savings, TransactionType.DEPOSIT, "20");
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(savings);

        applier.apply(transaction);

        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("120");
        assertThat(savings.getAvailableBalance()).isEqualByComparingTo("120");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.COMPLETED);
        assertThat(transaction.getPostedAt()).isEqualTo(NOW);
        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(postingService).post(eq(transaction), posting.capture(), eq(NOW));
        assertThat(posting.getValue().event()).isEqualTo(PostingEvent.COLLECTOR_DEPOSIT);
        assertThat(posting.getValue().amounts()).containsEntry(AmountPart.PRINCIPAL, new BigDecimal("20"));
        verify(transactionSmsService).deposit(transaction, savings);
    }

    @Test
    void sharePurchaseUsesTheShareRulesAndMessage() {
        MemberAccount shares = account(AccountType.SHARES, "50");
        Transaction transaction = pending(shares, TransactionType.SHARES, "15");
        when(accountLockService.lockForTransaction("1000000001")).thenReturn(shares);

        applier.apply(transaction);

        assertThat(shares.getCurrentBalance()).isEqualByComparingTo("65");
        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(postingService).post(eq(transaction), posting.capture(), eq(NOW));
        assertThat(posting.getValue().event()).isEqualTo(PostingEvent.COLLECTOR_SHARE_PURCHASE);
        verify(transactionSmsService).sharePurchase(transaction, shares);
    }

    // The cash is already with the collector, so the payment is marked FAILED rather than lost.
    @Test
    void accountThatIsNoLongerActiveMarksThePaymentFailed() {
        MemberAccount savings = account(AccountType.SAVINGS, "100");
        Transaction transaction = pending(savings, TransactionType.DEPOSIT, "20");
        when(accountLockService.lockForTransaction("1000000002"))
                .thenThrow(new ConflictException("Account 1000000002 is FROZEN"));

        applier.apply(transaction);

        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.FAILED);
        assertThat(transaction.getFailureReason()).isEqualTo("Account 1000000002 is FROZEN");
        verifyNoInteractions(postingService, transactionSmsService);
    }

    @Test
    void alreadyAppliedPaymentIsLeftAlone() {
        Transaction transaction = pending(account(AccountType.SAVINGS, "100"), TransactionType.DEPOSIT, "20");
        transaction.setStatus(TransactionStatus.COMPLETED);

        applier.apply(transaction);

        verifyNoInteractions(accountLockService, postingService, transactionSmsService);
    }

    // A payment a correction is holding is skipped this run (SKIP LOCKED returns nothing).
    @Test
    void finaliserSkipsALockedPayment() {
        UUID id = UUID.randomUUID();
        when(transactionRepo.lockPendingCollectorPayment(id)).thenReturn(Optional.empty());

        applier.finaliseDue(id);

        verifyNoInteractions(accountLockService);
    }

    @Test
    void finaliserAppliesEveryDuePaymentAndCarriesOnAfterAFailure() {
        CollectorPaymentApplier mockApplier = mock(CollectorPaymentApplier.class);
        CollectorPaymentFinalizer finalizer = new CollectorPaymentFinalizer(transactionRepo, mockApplier);
        finalizer.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(transactionRepo.findDuePendingCollectorPayments(NOW.minusMinutes(10), 100)).thenReturn(List.of(first, second));
        doThrow(new IllegalStateException("ledger broken")).when(mockApplier).finaliseDue(first);

        finalizer.finaliseDuePayments();

        verify(mockApplier).finaliseDue(first);
        verify(mockApplier).finaliseDue(second);
    }
}
