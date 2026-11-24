package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.statement.AccountStatement;
import com.bynexlabs.bankingbackend.DTO.statement.StatementLine;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountStatementServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 12, 2, 9, 30);
    private static final LocalDate FROM = LocalDate.of(2026, 11, 1);
    private static final LocalDate TO = LocalDate.of(2026, 11, 30);

    @Mock
    private MemberAccountRepo memberAccountRepo;
    @Mock
    private TransactionRepo transactionRepo;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AccountStatementPdf pdf;

    private AccountStatementService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch bogoso = Branch.builder().id(UUID.randomUUID()).name("Bogoso").build();
    private final Member member = Member.builder().id(UUID.randomUUID()).firstName("Ama").lastName("Mensah").branch(tarkwa).build();
    private final MemberAccount savings = account("1000000002", AccountType.SAVINGS, "470");
    private final MemberAccount shares = account("1000000001", AccountType.SHARES, "50");
    private final MemberAccount other = account("1000000009", AccountType.SAVINGS, "0");
    private final Staff tarkwaAgent = Staff.builder().id(UUID.randomUUID()).staffId("900120").branch(tarkwa).build();
    private final Staff bogosoAgent = Staff.builder().id(UUID.randomUUID()).staffId("900130").branch(bogoso).build();

    @BeforeEach
    void setUp() {
        service = new AccountStatementService(memberAccountRepo, transactionRepo, auditLogService, pdf);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        ReflectionTestUtils.setField(service, "creditUnionName", "Test Credit Union");
    }

    private MemberAccount account(String number, AccountType type, String balance) {
        return MemberAccount.builder().id(UUID.randomUUID()).member(member).accountNumber(number).accountType(type)
                .currentBalance(new BigDecimal(balance)).availableBalance(new BigDecimal(balance)).build();
    }

    private static Transaction transaction(TransactionType type, MemberAccount account, String amount, LocalDateTime at) {
        return Transaction.builder().id(UUID.randomUUID()).idempotencyKey(UUID.randomUUID().toString())
                .transactionType(type).amount(new BigDecimal(amount)).memberAccount(account)
                .status(TransactionStatus.COMPLETED).channel(TransactionChannel.OFFICE)
                .feeAmount(BigDecimal.ZERO).shareAmount(BigDecimal.ZERO).createdAt(at).receivedAt(at).build();
    }

    private void accountExists(MemberAccount account) {
        when(memberAccountRepo.findWithMemberByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));
    }

    // Balance now 470. Since 1 Nov: +100, -50 and -2 fee, -30, +20, and +40 on 1 Dec (after the
    // period). Opening = 470 - 78 = 392; the period ends at 430.
    @Test
    void balancesAreWorkedOutBackFromTheCurrentBalance() {
        accountExists(savings);
        Transaction deposit = transaction(TransactionType.DEPOSIT, savings, "100", LocalDateTime.of(2026, 11, 2, 10, 0));
        Transaction withdrawal = transaction(TransactionType.WITHDRAWAL, savings, "50", LocalDateTime.of(2026, 11, 3, 11, 0));
        withdrawal.setFeeAmount(new BigDecimal("2"));
        Transaction transferOut = transaction(TransactionType.TRANSFER, savings, "30", LocalDateTime.of(2026, 11, 5, 12, 0));
        transferOut.setCounterpartyAccount(other);
        Transaction transferIn = transaction(TransactionType.TRANSFER, other, "20", LocalDateTime.of(2026, 11, 6, 13, 0));
        transferIn.setCounterpartyAccount(savings);
        Transaction afterPeriod = transaction(TransactionType.DEPOSIT, savings, "40", LocalDateTime.of(2026, 12, 1, 8, 0));
        afterPeriod.setChannel(TransactionChannel.COLLECTOR);
        when(transactionRepo.findCompletedForAccountSince(savings.getId(), FROM.atStartOfDay()))
                .thenReturn(List.of(deposit, withdrawal, transferOut, transferIn, afterPeriod));

        AccountStatement statement = service.build("1000000002", FROM, TO, tarkwaAgent, false);

        assertThat(statement.openingBalance()).isEqualByComparingTo("392");
        assertThat(statement.closingBalance()).isEqualByComparingTo("430");
        assertThat(statement.totalIn()).isEqualByComparingTo("120");
        assertThat(statement.totalOut()).isEqualByComparingTo("82");
        assertThat(statement.lines()).extracting(StatementLine::getDetails).containsExactly(
                "Deposit", "Withdrawal", "Withdrawal fee", "Transfer to 1000000009", "Transfer from 1000000009");
        assertThat(statement.lines()).extracting(StatementLine::getBalance).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("492"), new BigDecimal("442"), new BigDecimal("440"),
                        new BigDecimal("410"), new BigDecimal("430"));
        assertThat(statement.lines().get(0).getMoneyIn()).isEqualByComparingTo("100");
        assertThat(statement.lines().get(0).getMoneyOut()).isNull();
        assertThat(statement.lines().get(0).getDate()).isEqualTo("02/11/2026 10:00");
        assertThat(statement.lines().get(2).getMoneyOut()).isEqualByComparingTo("2");
        assertThat(statement.creditUnionName()).isEqualTo("Test Credit Union");
        assertThat(statement.branchName()).isEqualTo("Tarkwa");
        assertThat(statement.memberName()).isEqualTo("Ama Mensah");
        assertThat(statement.accountType()).isEqualTo("Savings");
        assertThat(statement.generatedAt()).isEqualTo(NOW);
    }

    // A first deposit splits: the remainder on the savings account, the share part on shares.
    @Test
    void firstDepositShowsEachPartOnItsOwnAccount() {
        Transaction first = transaction(TransactionType.DEPOSIT, savings, "150", LocalDateTime.of(2026, 11, 10, 9, 0));
        first.setShareAmount(new BigDecimal("50"));
        first.setCounterpartyAccount(shares);

        assertThat(AccountStatementService.movements(first, savings.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.details()).isEqualTo("First deposit (GHS 50.00 to shares)");
                    assertThat(movement.amount()).isEqualByComparingTo("100");
                });
        assertThat(AccountStatementService.movements(first, shares.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.details()).isEqualTo("Shares from first deposit");
                    assertThat(movement.amount()).isEqualByComparingTo("50");
                });
    }

    @Test
    void collectorPaymentsAreMarkedAndCarryTheirDescription() {
        Transaction payment = transaction(TransactionType.SHARES, shares, "15", LocalDateTime.of(2026, 11, 12, 15, 0));
        payment.setChannel(TransactionChannel.COLLECTOR);
        payment.setDescription("market day");

        assertThat(AccountStatementService.movements(payment, shares.getId()))
                .singleElement()
                .satisfies(movement -> assertThat(movement.details()).isEqualTo("Share purchase by collector - market day"));
    }

    // Loans (V46): the payout with its fee as a separate row, and each installment taken.
    @Test
    void loanPayoutAndRepaymentsShowOnTheStatement() {
        Transaction payout = transaction(TransactionType.LOAN_DISBURSEMENT, savings, "1000", LocalDateTime.of(2026, 11, 14, 10, 0));
        payout.setFeeAmount(new BigDecimal("20"));
        payout.setDescription("Susu Loan loan");
        Transaction installment = transaction(TransactionType.LOAN_PAYMENT, savings, "25", LocalDateTime.of(2026, 12, 14, 23, 30));
        installment.setDescription("Installment 1 of 6");

        assertThat(AccountStatementService.movements(payout, savings.getId()))
                .extracting(AccountStatementService.Movement::details)
                .containsExactly("Loan paid in - Susu Loan loan", "Loan processing fee");
        assertThat(AccountStatementService.movements(payout, savings.getId()))
                .extracting(AccountStatementService.Movement::amount).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("1000"), new BigDecimal("-20"));
        assertThat(AccountStatementService.movements(installment, savings.getId()))
                .singleElement()
                .satisfies(movement -> {
                    assertThat(movement.details()).isEqualTo("Loan repayment - Installment 1 of 6");
                    assertThat(movement.amount()).isEqualByComparingTo("-25");
                });
    }

    @Test
    void emptyPeriodKeepsTheBalance() {
        accountExists(savings);
        when(transactionRepo.findCompletedForAccountSince(savings.getId(), FROM.atStartOfDay())).thenReturn(new ArrayList<>());

        AccountStatement statement = service.build("1000000002", FROM, TO, tarkwaAgent, false);

        assertThat(statement.lines()).isEmpty();
        assertThat(statement.openingBalance()).isEqualByComparingTo("470");
        assertThat(statement.closingBalance()).isEqualByComparingTo("470");
    }

    // Owner decision 2026-11-23: agents for their own branch only; administrators for any.
    @Test
    void agentFromAnotherBranchIsRefused() {
        accountExists(savings);

        assertThatThrownBy(() -> service.build("1000000002", FROM, TO, bogosoAgent, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("This member is from another branch");
    }

    @Test
    void administratorCanGetAnyBranchsStatement() {
        accountExists(savings);
        when(transactionRepo.findCompletedForAccountSince(savings.getId(), FROM.atStartOfDay())).thenReturn(List.of());

        assertThat(service.build("1000000002", FROM, TO, bogosoAgent, true).accountNumber()).isEqualTo("1000000002");
    }

    @Test
    void fromAfterToIsRejected() {
        assertThatThrownBy(() -> service.build("1000000002", TO, FROM, tarkwaAgent, false))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("from can't be after to");
    }

    // Owner decision 2026-11-23: every statement generated is in the audit log.
    @Test
    void generatingAStatementIsAudited() {
        accountExists(savings);
        when(transactionRepo.findCompletedForAccountSince(savings.getId(), FROM.atStartOfDay())).thenReturn(List.of());
        when(pdf.render(any(AccountStatement.class))).thenReturn(new byte[]{1, 2, 3});

        byte[] file = service.statementPdf("1000000002", FROM, TO, tarkwaAgent, false);

        assertThat(file).containsExactly(1, 2, 3);
        verify(auditLogService).recordAs("900120", AuditAction.ACCOUNT_STATEMENT_GENERATED, AuditTargetType.ACCOUNT,
                "1000000002", "Statement from 2026-11-01 to 2026-11-30");
    }
}
