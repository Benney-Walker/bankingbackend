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
import com.bynexlabs.bankingbackend.Utility.LoanRules;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.ApplicationStatus;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import com.bynexlabs.bankingbackend.models.LoanAccount;
import com.bynexlabs.bankingbackend.models.LoanApplication;
import com.bynexlabs.bankingbackend.models.LoanGuarantor;
import com.bynexlabs.bankingbackend.models.LoanProduct;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.service.PostingService.Posting;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Loan applications (owner decisions 2026-11-24):
 * - agents and administrators apply for a member of any branch;
 * - the member must be ACTIVE (KYC done), registered at least loanMinMembershipMonths (financial
 *   settings) ago, and have no loan being repaid and no other waiting application;
 * - the product's number of guarantors: other ACTIVE members, recorded only;
 * - administrators approve, possibly a lower amount, or reject with a reason; as the top role they
 *   may also review an application they entered (owner, 2026-11-24). Approval pays the loan
 *   into the member's savings or current account at once, minus the product's processing fee,
 *   and sets up the monthly installments (flat interest at the product's yearly rate);
 * - every step is audited; the member is texted when the loan is paid in.
 */
@Service
@RequiredArgsConstructor
public class LoanApplicationService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final LoanApplicationRepo applicationRepo;

    private final LoanGuarantorRepo guarantorRepo;

    private final LoanProductRepo productRepo;

    private final LoanAccountRepo loanAccountRepo;

    private final AmortizationScheduleRepo scheduleRepo;

    private final MemberRepository memberRepository;

    private final MemberAccountRepo memberAccountRepo;

    private final AccountLockService accountLockService;

    private final FinancialSettingsService settingsService;

    private final TransactionService transactionService;

    private final PostingService postingService;

    private final TransactionSmsService transactionSmsService;

    private final AuditLogService auditLogService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    @Transactional
    public LoanApplicationResponse apply(LoanApplicationRequest request, Staff staff) {
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        LoanProduct product = productRepo.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan product not found"));
        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ConflictException("The loan product " + product.getName() + " is not offered at the moment");
        }
        requireWithinProduct(product, amount, request.getTermMonths());

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        LocalDate today = LocalDate.now(clock);
        requireEligible(member, today);
        repaymentAccount(member);
        if (applicationRepo.existsByMember_IdAndStatus(member.getId(), ApplicationStatus.PENDING)) {
            throw new ConflictException("This member already has a loan application waiting for review");
        }
        List<Member> guarantors = guarantors(request.getGuarantorMemberIds(), member, product);

        LocalDateTime now = LocalDateTime.now(clock);
        LoanApplication application;
        try {
            application = applicationRepo.saveAndFlush(LoanApplication.builder()
                    .member(member)
                    .loanProduct(product)
                    .requestedAmount(amount)
                    .requestedTermMonths(request.getTermMonths())
                    .purpose(request.getPurpose() == null || request.getPurpose().isBlank() ? null : request.getPurpose().trim())
                    .status(ApplicationStatus.PENDING)
                    .appliedBy(staff)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            // Two applications for the same member at once: the unique index lets only one in.
            throw new ConflictException("This member already has a loan application waiting for review");
        }
        List<LoanGuarantor> saved = guarantorRepo.saveAll(guarantors.stream()
                .map(g -> LoanGuarantor.builder().loanApplication(application).member(g).build())
                .toList());
        auditLogService.recordAs(staff.getStaffId(), AuditAction.LOAN_APPLICATION_SUBMITTED, AuditTargetType.LOAN_APPLICATION,
                application.getId().toString(), "Member " + member.getId() + ": GHS " + MoneyMovementService.ghs(amount)
                        + " for " + request.getTermMonths() + " months (" + product.getName() + "), "
                        + guarantors.size() + " guarantor(s)");
        return LoanApplicationResponse.from(application, saved, null);
    }

    /**
     * Approves and pays out at once. Locks the application (a second approval or a rejection in
     * between gets 409, so the loan can't be paid twice), then the member's account.
     */
    @Transactional
    public LoanApplicationResponse approve(UUID applicationId, BigDecimal approvedAmount, Staff admin) {
        LoanApplication application = lockPending(applicationId);
        LoanProduct product = application.getLoanProduct();
        if (!Boolean.TRUE.equals(product.getIsActive())) {
            throw new ConflictException("The loan product " + product.getName() + " is not offered at the moment");
        }
        BigDecimal amount = approvedAmount == null ? application.getRequestedAmount() : MoneyRules.requireValidAmount(approvedAmount);
        if (amount.compareTo(application.getRequestedAmount()) > 0) {
            throw new BadRequestException("The approved amount can't be more than the GHS "
                    + MoneyMovementService.ghs(application.getRequestedAmount()) + " asked for");
        }
        if (amount.compareTo(product.getMinAmount()) < 0) {
            throw new BadRequestException("The approved amount can't be less than the product's minimum of GHS "
                    + MoneyMovementService.ghs(product.getMinAmount()));
        }
        int months = application.getRequestedTermMonths();

        Member member = application.getMember();
        MemberAccount account = accountLockService.lockForTransaction(repaymentAccount(member).getAccountNumber());
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        requireEligible(member, today);

        BigDecimal fee = LoanRules.processingFee(product.getProcessingFeeType(), product.getProcessingFeeValue(), amount);
        if (fee.compareTo(amount) >= 0) {
            throw new ConflictException("The processing fee of GHS " + MoneyMovementService.ghs(fee)
                    + " would take the whole loan; approve a larger amount or change the product's fee");
        }
        BigDecimal interest = LoanRules.totalInterest(amount, product.getBaseInterestRate(), months);
        BigDecimal paidIn = amount.subtract(fee);

        // The payout: one transaction, the fee kept back.
        account.setCurrentBalance(account.getCurrentBalance().add(paidIn));
        account.setAvailableBalance(account.getAvailableBalance().add(paidIn));
        Transaction payout = transactionService.record(Transaction.builder()
                .idempotencyKey("loan-disbursement-" + application.getId())
                .transactionType(TransactionType.LOAN_DISBURSEMENT)
                .amount(amount)
                .feeAmount(fee)
                .memberAccount(account)
                .initiatedBy(admin)
                .description(product.getName() + " loan")
                .status(TransactionStatus.COMPLETED)
                .channel(TransactionChannel.OFFICE)
                .createdAt(now)
                .postedAt(now)
                .build());
        postingService.post(payout, Posting.of(PostingEvent.LOAN_DISBURSEMENT, account.getAccountType(), null)
                .with(AmountPart.PRINCIPAL, amount)
                .with(AmountPart.REMAINDER, paidIn)
                .with(AmountPart.FEE, fee), now);

        List<LoanRules.Installment> plan = LoanRules.schedule(amount, interest, months, today);
        LoanAccount loan;
        try {
            loan = loanAccountRepo.saveAndFlush(LoanAccount.builder()
                    .loanApplication(application)
                    .member(member)
                    .memberAccount(account)
                    .principalAmount(amount)
                    .currentInterestRate(product.getBaseInterestRate())
                    .totalInterest(interest)
                    .processingFee(fee)
                    .termMonths(months)
                    .disbursementDate(today)
                    .maturityDate(plan.get(plan.size() - 1).dueDate())
                    .disbursementTransaction(payout)
                    .status(LoanAccountStatus.ACTIVE)
                    .createdAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("This member already has a loan being repaid");
        }
        List<AmortizationSchedule> installments = scheduleRepo.saveAll(plan.stream()
                .map(i -> AmortizationSchedule.builder()
                        .loanAccount(loan)
                        .installmentNumber(i.number())
                        .dueDate(i.dueDate())
                        .expectedPrincipal(i.principal())
                        .expectedInterest(i.interest())
                        .expectedTotal(i.total())
                        .status(ScheduleStatus.PENDING)
                        .build())
                .toList());

        application.setApprovedAmount(amount);
        application.setStatus(ApplicationStatus.APPROVED);
        application.setReviewedBy(admin);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);

        transactionSmsService.loanPaidIn(payout, account, loan, installments.get(0));
        auditLogService.recordAs(admin.getStaffId(), AuditAction.LOAN_APPROVED, AuditTargetType.LOAN_APPLICATION,
                application.getId().toString(), "Approved GHS " + MoneyMovementService.ghs(amount) + " of GHS "
                        + MoneyMovementService.ghs(application.getRequestedAmount()) + " for " + months + " months; fee GHS "
                        + MoneyMovementService.ghs(fee) + ", interest GHS " + MoneyMovementService.ghs(interest)
                        + "; paid into account " + account.getAccountNumber());
        return LoanApplicationResponse.from(application, guarantorRepo.findByLoanApplication_Id(application.getId()), loan.getId());
    }

    @Transactional
    public LoanApplicationResponse reject(UUID applicationId, String reason, Staff admin) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required to reject an application");
        }
        LoanApplication application = lockPending(applicationId);
        LocalDateTime now = LocalDateTime.now(clock);
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(reason.trim());
        application.setReviewedBy(admin);
        application.setReviewedAt(now);
        application.setUpdatedAt(now);
        auditLogService.recordAs(admin.getStaffId(), AuditAction.LOAN_REJECTED, AuditTargetType.LOAN_APPLICATION,
                application.getId().toString(), "Rejected: " + reason.trim());
        return LoanApplicationResponse.from(application, guarantorRepo.findByLoanApplication_Id(application.getId()), null);
    }

    /** Applications by status (all when none is given), newest first. Any branch. */
    @Transactional(readOnly = true)
    public PagedModel<LoanApplicationResponse> list(ApplicationStatus status, UUID memberId, int page, int size) {
        PageRequest pageRequest = pageRequest(page, size);
        var applications = memberId != null ? applicationRepo.findByMember_Id(memberId, pageRequest)
                : status != null ? applicationRepo.findByStatus(status, pageRequest)
                : applicationRepo.findAllBy(pageRequest);
        return new PagedModel<>(applications.map(a -> LoanApplicationResponse.from(a, null, null)));
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse get(UUID applicationId) {
        LoanApplication application = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));
        UUID loanId = application.getStatus() == ApplicationStatus.APPROVED
                ? loanAccountRepo.findByLoanApplication_Id(applicationId).map(LoanAccount::getId).orElse(null)
                : null;
        return LoanApplicationResponse.from(application, guarantorRepo.findByLoanApplication_Id(applicationId), loanId);
    }

    // --- rules ---

    private void requireEligible(Member member, LocalDate today) {
        if (member.getMembershipStatus() != MembershipStatus.ACTIVE) {
            throw new ConflictException("Only active members (KYC done) can get a loan; this member is "
                    + member.getMembershipStatus());
        }
        int months = settingsService.current().getLoanMinMembershipMonths();
        LocalDate eligibleFrom = member.getCreatedAt().toLocalDate().plusMonths(months);
        if (eligibleFrom.isAfter(today)) {
            throw new ConflictException("A member needs " + months + " months of membership before a loan. This member joined on "
                    + member.getCreatedAt().toLocalDate().format(DAY) + " and can apply from " + eligibleFrom.format(DAY));
        }
        if (loanAccountRepo.existsByMember_IdAndStatus(member.getId(), LoanAccountStatus.ACTIVE)) {
            throw new ConflictException("This member already has a loan being repaid; one loan at a time");
        }
    }

    private static void requireWithinProduct(LoanProduct product, BigDecimal amount, Integer termMonths) {
        if (amount.compareTo(product.getMinAmount()) < 0 || amount.compareTo(product.getMaxAmount()) > 0) {
            throw new BadRequestException("The amount must be between GHS " + MoneyMovementService.ghs(product.getMinAmount())
                    + " and GHS " + MoneyMovementService.ghs(product.getMaxAmount()) + " for " + product.getName());
        }
        if (termMonths == null || termMonths < 1 || termMonths > product.getMaxTermMonths()) {
            throw new BadRequestException("The term must be between 1 and " + product.getMaxTermMonths() + " months for "
                    + product.getName());
        }
    }

    /** The member's savings or current account: the loan is paid into it and repaid from it. */
    private MemberAccount repaymentAccount(Member member) {
        MemberAccount account = memberAccountRepo.findByMember_Id(member.getId()).stream()
                .filter(a -> a.getAccountType() != AccountType.SHARES)
                .findFirst()
                .orElseThrow(() -> new ConflictException("This member has no savings or current account to pay the loan into"));
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("The member's account " + account.getAccountNumber() + " is " + account.getStatus());
        }
        return account;
    }

    private List<Member> guarantors(List<UUID> ids, Member borrower, LoanProduct product) {
        List<UUID> requested = ids == null ? List.of() : ids;
        Set<UUID> distinct = new HashSet<>(requested);
        if (distinct.size() != requested.size()) {
            throw new BadRequestException("The same guarantor is listed twice");
        }
        if (distinct.size() != product.getGuarantorsRequired()) {
            throw new BadRequestException(product.getName() + " needs " + product.getGuarantorsRequired()
                    + " guarantor(s); " + distinct.size() + " given");
        }
        if (distinct.contains(borrower.getId())) {
            throw new BadRequestException("A member can't guarantee their own loan");
        }
        List<Member> members = memberRepository.findAllById(distinct);
        if (members.size() != distinct.size()) {
            throw new ResourceNotFoundException("Guarantor not found");
        }
        for (Member guarantor : members) {
            if (guarantor.getMembershipStatus() != MembershipStatus.ACTIVE) {
                throw new ConflictException("Guarantor " + guarantor.getFirstName() + " " + guarantor.getLastName()
                        + " is not an active member");
            }
        }
        return members;
    }

    private LoanApplication lockPending(UUID applicationId) {
        LoanApplication application = applicationRepo.lockById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));
        if (application.getStatus() != ApplicationStatus.PENDING) {
            throw new ConflictException("This application was already " + application.getStatus().name().toLowerCase(Locale.ENGLISH));
        }
        return application;
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
