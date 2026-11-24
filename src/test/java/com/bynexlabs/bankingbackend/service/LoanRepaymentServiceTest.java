package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.AmortizationScheduleRepo;
import com.bynexlabs.bankingbackend.Repository.LoanAccountRepo;
import com.bynexlabs.bankingbackend.Repository.LoanRepaymentRepo;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import com.bynexlabs.bankingbackend.models.LoanAccount;
import com.bynexlabs.bankingbackend.models.LoanRepayment;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 24, 23, 30);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Mock private LoanAccountRepo loanAccountRepo;
    @Mock private AmortizationScheduleRepo scheduleRepo;
    @Mock private LoanRepaymentRepo repaymentRepo;
    @Mock private MemberAccountRepo memberAccountRepo;
    @Mock private TransactionRepo transactionRepo;
    @Mock private TransactionService transactionService;
    @Mock private PostingService postingService;
    @Mock private TransactionSmsService transactionSmsService;

    private LoanRepaymentService service;

    private final Member member = Member.builder().id(UUID.randomUUID()).firstName("Ama").lastName("Mensah").build();
    private MemberAccount savings;
    private LoanAccount loan;
    private final List<AmortizationSchedule> installments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new LoanRepaymentService(loanAccountRepo, scheduleRepo, repaymentRepo, memberAccountRepo, transactionRepo,
                transactionService, postingService, transactionSmsService);
        savings = account("50");
        loan = LoanAccount.builder().id(UUID.randomUUID()).member(member).memberAccount(savings).termMonths(2)
                .principalAmount(new BigDecimal("80")).totalInterest(new BigDecimal("20")).status(LoanAccountStatus.ACTIVE).build();
        installments.add(installment(1, "40", "10", TODAY));
        installments.add(installment(2, "40", "10", TODAY.plusMonths(1)));
        lenient().when(loanAccountRepo.lockById(loan.getId())).thenReturn(Optional.of(loan));
        lenient().when(memberAccountRepo.lockById(savings.getId())).thenReturn(Optional.of(savings));
        lenient().when(scheduleRepo.findByLoanAccount_IdOrderByInstallmentNumberAsc(loan.getId())).thenReturn(installments);
        lenient().when(transactionService.record(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
    }

    private MemberAccount account(String balance) {
        return MemberAccount.builder().id(UUID.randomUUID()).member(member).accountNumber("1000000002")
                .accountType(AccountType.SAVINGS).status(AccountStatus.ACTIVE)
                .currentBalance(new BigDecimal(balance)).availableBalance(new BigDecimal(balance)).build();
    }

    private AmortizationSchedule installment(int number, String principal, String interest, LocalDate due) {
        BigDecimal p = new BigDecimal(principal);
        BigDecimal i = new BigDecimal(interest);
        return AmortizationSchedule.builder().id(UUID.randomUUID()).loanAccount(loan).installmentNumber(number).dueDate(due)
                .expectedPrincipal(p).expectedInterest(i).expectedTotal(p.add(i)).status(ScheduleStatus.PENDING).build();
    }

    private static BigDecimal amount(String expected) {
        return argThat(value -> value != null && value.compareTo(new BigDecimal(expected)) == 0);
    }

    private void due(AmortizationSchedule... dueNow) {
        when(scheduleRepo.findDueUnpaid(loan.getId(), TODAY)).thenReturn(List.of(dueNow));
    }

    @Test
    void fullInstallmentIsTakenOnTheDueDate() {
        savings.setCurrentBalance(new BigDecimal("80"));
        savings.setAvailableBalance(new BigDecimal("80"));
        due(installments.get(0));

        service.collect(loan.getId(), NOW);

        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("30");
        assertThat(installments.get(0).getStatus()).isEqualTo(ScheduleStatus.PAID);
        assertThat(installments.get(0).getPaidAt()).isEqualTo(NOW);
        ArgumentCaptor<Transaction> taken = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(taken.capture());
        assertThat(taken.getValue().getTransactionType()).isEqualTo(TransactionType.LOAN_PAYMENT);
        assertThat(taken.getValue().getChannel()).isEqualTo(TransactionChannel.SYSTEM);
        assertThat(taken.getValue().getInitiatedBy()).isNull();
        assertThat(taken.getValue().getDescription()).isEqualTo("Installment 1 of 2");
        assertThat(taken.getValue().getIdempotencyKey())
                .isEqualTo("loan-installment-" + installments.get(0).getId() + "-2026-12-24");
        verify(transactionSmsService).loanInstallment(any(), eq(savings), eq(installments.get(0)), eq(2),
                amount("50"));
        assertThat(loan.getStatus()).isEqualTo(LoanAccountStatus.ACTIVE);
    }

    // Owner's example: 50 due, 25 in the account → 25 taken (20 loan + 5 interest in the
    // ledger), 25 left and overdue; the account can be emptied.
    @Test
    void whatsThereIsTakenAndTheRestIsOverdue() {
        savings.setCurrentBalance(new BigDecimal("25"));
        savings.setAvailableBalance(new BigDecimal("25"));
        due(installments.get(0));

        service.collect(loan.getId(), NOW);

        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("0");
        AmortizationSchedule first = installments.get(0);
        assertThat(first.getStatus()).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(first.remaining()).isEqualByComparingTo("25");
        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(postingService).post(any(), posting.capture(), eq(NOW));
        assertThat(posting.getValue().event()).isEqualTo(PostingEvent.LOAN_REPAYMENT);
        assertThat(posting.getValue().amounts().get(AmountPart.PRINCIPAL)).isEqualByComparingTo("25");
        assertThat(posting.getValue().amounts().get(AmountPart.LOAN_PRINCIPAL)).isEqualByComparingTo("20");
        assertThat(posting.getValue().amounts().get(AmountPart.INTEREST)).isEqualByComparingTo("5");
        ArgumentCaptor<LoanRepayment> repayment = ArgumentCaptor.forClass(LoanRepayment.class);
        verify(repaymentRepo).save(repayment.capture());
        assertThat(repayment.getValue().getPrincipalPart()).isEqualByComparingTo("20");
        assertThat(repayment.getValue().getInterestPart()).isEqualByComparingTo("5");
        // Left on the installment 25; on the loan 25 + 50.
        verify(transactionSmsService).loanInstallment(any(), eq(savings), eq(first), eq(2), amount("75"));
    }

    @Test
    void emptyAccountLeavesTheInstallmentOverdueWithNothingTaken() {
        savings.setCurrentBalance(BigDecimal.ZERO);
        savings.setAvailableBalance(BigDecimal.ZERO);
        due(installments.get(0));

        service.collect(loan.getId(), NOW);

        assertThat(installments.get(0).getStatus()).isEqualTo(ScheduleStatus.OVERDUE);
        verify(transactionService, never()).record(any());
        verify(transactionSmsService, never()).loanInstallment(any(), any(), any(), any(Integer.class), any());
    }

    // Retried every day: an overdue installment is cleared first, oldest first.
    @Test
    void overdueInstallmentsAreClearedOldestFirst() {
        AmortizationSchedule first = installments.get(0);
        first.setStatus(ScheduleStatus.OVERDUE);
        first.setPaidPrincipal(new BigDecimal("20"));
        first.setPaidInterest(new BigDecimal("5"));
        AmortizationSchedule second = installments.get(1);
        savings.setCurrentBalance(new BigDecimal("40"));
        savings.setAvailableBalance(new BigDecimal("40"));
        due(first, second);

        service.collect(loan.getId(), NOW);

        assertThat(first.getStatus()).isEqualTo(ScheduleStatus.PAID);
        assertThat(second.remaining()).isEqualByComparingTo("35");
        assertThat(second.getStatus()).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("0");
    }

    // Each installment is tried once a day, so a second run the same day takes nothing.
    @Test
    void sameDayRerunTakesNothingMore() {
        due(installments.get(0));
        when(transactionRepo.findByIdempotencyKey("loan-installment-" + installments.get(0).getId() + "-2026-12-24"))
                .thenReturn(Optional.of(new Transaction()));

        service.collect(loan.getId(), NOW);

        verify(transactionService, never()).record(any());
        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("50");
    }

    @Test
    void loanClosesWhenEveryInstallmentIsPaid() {
        AmortizationSchedule first = installments.get(0);
        first.setStatus(ScheduleStatus.PAID);
        first.setPaidPrincipal(new BigDecimal("40"));
        first.setPaidInterest(new BigDecimal("10"));
        due(installments.get(1));

        service.collect(loan.getId(), NOW);

        assertThat(installments.get(1).getStatus()).isEqualTo(ScheduleStatus.PAID);
        assertThat(loan.getStatus()).isEqualTo(LoanAccountStatus.CLOSED_PAID);
        assertThat(loan.getClosedAt()).isEqualTo(NOW);
    }

    @Test
    void frozenAccountIsNotTouched() {
        savings.setStatus(AccountStatus.FROZEN);
        due(installments.get(0));

        service.collect(loan.getId(), NOW);

        verify(transactionService, never()).record(any());
        assertThat(installments.get(0).getStatus()).isEqualTo(ScheduleStatus.OVERDUE);
    }
}
