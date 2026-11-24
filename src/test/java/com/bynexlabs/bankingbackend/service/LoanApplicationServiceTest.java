package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.loan.LoanApplicationRequest;
import com.bynexlabs.bankingbackend.DTO.loan.LoanApplicationResponse;
import com.bynexlabs.bankingbackend.Repository.AmortizationScheduleRepo;
import com.bynexlabs.bankingbackend.Repository.LoanAccountRepo;
import com.bynexlabs.bankingbackend.Repository.LoanApplicationRepo;
import com.bynexlabs.bankingbackend.Repository.LoanGuarantorRepo;
import com.bynexlabs.bankingbackend.Repository.LoanProductRepo;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.ApplicationStatus;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.FinancialSettings;
import com.bynexlabs.bankingbackend.models.LoanAccount;
import com.bynexlabs.bankingbackend.models.LoanApplication;
import com.bynexlabs.bankingbackend.models.LoanProduct;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanApplicationServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 24, 10, 0);

    @Mock private LoanApplicationRepo applicationRepo;
    @Mock private LoanGuarantorRepo guarantorRepo;
    @Mock private LoanProductRepo productRepo;
    @Mock private LoanAccountRepo loanAccountRepo;
    @Mock private AmortizationScheduleRepo scheduleRepo;
    @Mock private MemberRepository memberRepository;
    @Mock private MemberAccountRepo memberAccountRepo;
    @Mock private AccountLockService accountLockService;
    @Mock private FinancialSettingsService settingsService;
    @Mock private TransactionService transactionService;
    @Mock private PostingService postingService;
    @Mock private TransactionSmsService transactionSmsService;
    @Mock private AuditLogService auditLogService;

    private LoanApplicationService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900120").branch(tarkwa).build();
    private final Staff admin = Staff.builder().id(UUID.randomUUID()).staffId("900001").build();
    private final Member member = member("Ama", "Mensah", LocalDateTime.of(2026, 1, 5, 9, 0));
    private final Member guarantor = member("Kofi", "Boateng", LocalDateTime.of(2025, 5, 1, 9, 0));
    private final MemberAccount savings = MemberAccount.builder().id(UUID.randomUUID()).member(member)
            .accountNumber("1000000002").accountType(AccountType.SAVINGS).status(AccountStatus.ACTIVE)
            .currentBalance(new BigDecimal("10")).availableBalance(new BigDecimal("10")).build();
    private final MemberAccount shares = MemberAccount.builder().id(UUID.randomUUID()).member(member)
            .accountNumber("1000000001").accountType(AccountType.SHARES).status(AccountStatus.ACTIVE)
            .currentBalance(new BigDecimal("50")).availableBalance(new BigDecimal("50")).build();
    private final LoanProduct product = LoanProduct.builder().id(1).name("Susu Loan").baseInterestRate(new BigDecimal("0.1000"))
            .minAmount(new BigDecimal("100")).maxAmount(new BigDecimal("5000")).maxTermMonths(12)
            .processingFeeType(LoanFeeType.FLAT).processingFeeValue(new BigDecimal("20")).guarantorsRequired(1)
            .isActive(true).build();

    private Member member(String first, String last, LocalDateTime joined) {
        return Member.builder().id(UUID.randomUUID()).firstName(first).lastName(last).branch(tarkwa)
                .membershipStatus(MembershipStatus.ACTIVE).createdAt(joined).build();
    }

    @BeforeEach
    void setUp() {
        service = new LoanApplicationService(applicationRepo, guarantorRepo, productRepo, loanAccountRepo, scheduleRepo,
                memberRepository, memberAccountRepo, accountLockService, settingsService, transactionService, postingService,
                transactionSmsService, auditLogService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        lenient().when(settingsService.current()).thenReturn(FinancialSettings.builder().loanMinMembershipMonths(3).build());
        lenient().when(memberAccountRepo.findByMember_Id(member.getId())).thenReturn(List.of(shares, savings));
    }

    private LoanApplicationRequest request() {
        return LoanApplicationRequest.builder().memberId(member.getId()).productId(1).amount(new BigDecimal("1000"))
                .termMonths(6).guarantorMemberIds(List.of(guarantor.getId())).purpose("Stock for the shop").build();
    }

    private void readyToApply() {
        when(productRepo.findById(1)).thenReturn(Optional.of(product));
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
    }

    // --- applying ---

    @Test
    void agentAppliesForAMemberWithTheirGuarantor() {
        readyToApply();
        when(memberRepository.findAllById(any())).thenReturn(List.of(guarantor));
        when(applicationRepo.saveAndFlush(any(LoanApplication.class))).thenAnswer(i -> {
            LoanApplication a = i.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(guarantorRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));

        LoanApplicationResponse response = service.apply(request(), agent);

        assertThat(response.status()).isEqualTo(ApplicationStatus.PENDING);
        assertThat(response.requestedAmount()).isEqualByComparingTo("1000");
        assertThat(response.appliedBy()).isEqualTo("900120");
        assertThat(response.guarantors()).extracting(LoanApplicationResponse.Guarantor::name).containsExactly("Kofi Boateng");
        verify(auditLogService).recordAs(eq("900120"), eq(AuditAction.LOAN_APPLICATION_SUBMITTED),
                eq(AuditTargetType.LOAN_APPLICATION), anyString(), anyString());
    }

    // Owner decision 2026-11-24: minimum membership time from financial settings.
    @Test
    void memberNeedsTheMinimumMembershipTime() {
        readyToApply();
        member.setCreatedAt(LocalDateTime.of(2026, 9, 1, 9, 0));

        assertThatThrownBy(() -> service.apply(request(), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A member needs 3 months of membership before a loan. This member joined on 1 Sep 2026 "
                        + "and can apply from 1 Dec 2026");
    }

    @Test
    void onlyActiveMembersCanApply() {
        readyToApply();
        member.setMembershipStatus(MembershipStatus.PENDING);

        assertThatThrownBy(() -> service.apply(request(), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Only active members");
    }

    // Owner decision 2026-11-24: one loan at a time.
    @Test
    void oneLoanAtATime() {
        readyToApply();
        when(loanAccountRepo.existsByMember_IdAndStatus(member.getId(), LoanAccountStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> service.apply(request(), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member already has a loan being repaid; one loan at a time");
    }

    @Test
    void oneWaitingApplicationPerMember() {
        readyToApply();
        when(applicationRepo.existsByMember_IdAndStatus(member.getId(), ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.apply(request(), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member already has a loan application waiting for review");
    }

    @Test
    void productDecidesHowManyGuarantors() {
        readyToApply();
        LoanApplicationRequest request = request();
        request.setGuarantorMemberIds(List.of());

        assertThatThrownBy(() -> service.apply(request, agent))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Susu Loan needs 1 guarantor(s); 0 given");
    }

    @Test
    void memberCantGuaranteeTheirOwnLoan() {
        readyToApply();
        LoanApplicationRequest request = request();
        request.setGuarantorMemberIds(List.of(member.getId()));

        assertThatThrownBy(() -> service.apply(request, agent))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A member can't guarantee their own loan");
    }

    @Test
    void amountAndTermMustFitTheProduct() {
        when(productRepo.findById(1)).thenReturn(Optional.of(product));
        LoanApplicationRequest tooBig = request();
        tooBig.setAmount(new BigDecimal("6000"));
        LoanApplicationRequest tooLong = request();
        tooLong.setTermMonths(13);

        assertThatThrownBy(() -> service.apply(tooBig, agent)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("between GHS 100.00 and GHS 5000.00");
        assertThatThrownBy(() -> service.apply(tooLong, agent)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("between 1 and 12 months");
    }

    // --- approving ---

    private LoanApplication pending() {
        return LoanApplication.builder().id(UUID.randomUUID()).member(member).loanProduct(product)
                .requestedAmount(new BigDecimal("1000")).requestedTermMonths(6).status(ApplicationStatus.PENDING)
                .appliedBy(agent).createdAt(NOW.minusDays(1)).updatedAt(NOW.minusDays(1)).build();
    }

    private void readyToApprove(LoanApplication application) {
        when(applicationRepo.lockById(application.getId())).thenReturn(Optional.of(application));
        when(accountLockService.lockForTransaction("1000000002")).thenReturn(savings);
        when(transactionService.record(any(Transaction.class))).thenAnswer(i -> {
            Transaction t = i.getArgument(0);
            t.setId(UUID.randomUUID());
            return t;
        });
        when(loanAccountRepo.saveAndFlush(any(LoanAccount.class))).thenAnswer(i -> {
            LoanAccount l = i.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });
        when(scheduleRepo.saveAll(anyList())).thenAnswer(i -> i.getArgument(0));
    }

    // Owner decision 2026-11-24: approval pays out at once, minus the processing fee.
    @Test
    void approvalPaysTheLoanInMinusTheFee() {
        LoanApplication application = pending();
        readyToApprove(application);

        LoanApplicationResponse response = service.approve(application.getId(), null, admin);

        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("990");
        assertThat(savings.getAvailableBalance()).isEqualByComparingTo("990");
        ArgumentCaptor<Transaction> payout = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(payout.capture());
        assertThat(payout.getValue().getTransactionType()).isEqualTo(TransactionType.LOAN_DISBURSEMENT);
        assertThat(payout.getValue().getAmount()).isEqualByComparingTo("1000");
        assertThat(payout.getValue().getFeeAmount()).isEqualByComparingTo("20");
        assertThat(payout.getValue().getChannel()).isEqualTo(TransactionChannel.OFFICE);
        assertThat(payout.getValue().getIdempotencyKey()).isEqualTo("loan-disbursement-" + application.getId());

        ArgumentCaptor<Posting> posting = ArgumentCaptor.forClass(Posting.class);
        verify(postingService).post(any(), posting.capture(), eq(NOW));
        assertThat(posting.getValue().event()).isEqualTo(PostingEvent.LOAN_DISBURSEMENT);
        assertThat(posting.getValue().amounts().get(AmountPart.PRINCIPAL)).isEqualByComparingTo("1000");
        assertThat(posting.getValue().amounts().get(AmountPart.REMAINDER)).isEqualByComparingTo("980");
        assertThat(posting.getValue().amounts().get(AmountPart.FEE)).isEqualByComparingTo("20");

        ArgumentCaptor<LoanAccount> loan = ArgumentCaptor.forClass(LoanAccount.class);
        verify(loanAccountRepo).saveAndFlush(loan.capture());
        assertThat(loan.getValue().getTotalInterest()).isEqualByComparingTo("50.00");
        assertThat(loan.getValue().getDisbursementDate()).isEqualTo(LocalDate.of(2026, 11, 24));
        assertThat(loan.getValue().getMaturityDate()).isEqualTo(LocalDate.of(2027, 5, 24));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AmortizationSchedule>> installments = ArgumentCaptor.forClass(List.class);
        verify(scheduleRepo).saveAll(installments.capture());
        assertThat(installments.getValue()).hasSize(6);
        assertThat(installments.getValue().get(0).getExpectedTotal()).isEqualByComparingTo("174.99");
        assertThat(installments.getValue().get(0).getDueDate()).isEqualTo(LocalDate.of(2026, 12, 24));

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(response.approvedAmount()).isEqualByComparingTo("1000");
        assertThat(response.reviewedBy()).isEqualTo("900001");
        verify(transactionSmsService).loanPaidIn(any(), eq(savings), any(), any());
        verify(auditLogService).recordAs(eq("900001"), eq(AuditAction.LOAN_APPROVED), eq(AuditTargetType.LOAN_APPLICATION),
                eq(application.getId().toString()), anyString());
    }

    // Owner decision 2026-11-24: the admin can approve less, never more.
    @Test
    void adminCanApproveALowerAmount() {
        LoanApplication application = pending();
        readyToApprove(application);

        LoanApplicationResponse response = service.approve(application.getId(), new BigDecimal("600"), admin);

        assertThat(response.approvedAmount()).isEqualByComparingTo("600");
        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("590");
    }

    @Test
    void cantApproveMoreThanAskedFor() {
        LoanApplication application = pending();
        when(applicationRepo.lockById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.approve(application.getId(), new BigDecimal("1500"), admin))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The approved amount can't be more than the GHS 1000.00 asked for");
        verify(transactionService, never()).record(any());
    }

    @Test
    void cantReviewAnApplicationYouEntered() {
        LoanApplication application = pending();
        application.setAppliedBy(admin);
        when(applicationRepo.lockById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.approve(application.getId(), null, admin)).isInstanceOf(ForbiddenException.class);
    }

    // The application row is locked, so a second approval can't pay the loan twice.
    @Test
    void reviewedApplicationCantBeApprovedAgain() {
        LoanApplication application = pending();
        application.setStatus(ApplicationStatus.APPROVED);
        when(applicationRepo.lockById(application.getId())).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> service.approve(application.getId(), null, admin))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This application was already approved");
        verify(transactionService, never()).record(any());
    }

    @Test
    void rejectionNeedsAReason() {
        assertThatThrownBy(() -> service.reject(UUID.randomUUID(), " ", admin)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectionIsRecorded() {
        LoanApplication application = pending();
        when(applicationRepo.lockById(application.getId())).thenReturn(Optional.of(application));

        LoanApplicationResponse response = service.reject(application.getId(), "Income too low", admin);

        assertThat(response.status()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Income too low");
        verify(auditLogService).recordAs("900001", AuditAction.LOAN_REJECTED, AuditTargetType.LOAN_APPLICATION,
                application.getId().toString(), "Rejected: Income too low");
    }
}
