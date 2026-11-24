package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.AmortizationScheduleRepo;
import com.bynexlabs.bankingbackend.Repository.LoanAccountRepo;
import com.bynexlabs.bankingbackend.Repository.LoanRepaymentRepo;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.LoanRules;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import com.bynexlabs.bankingbackend.models.LoanAccount;
import com.bynexlabs.bankingbackend.models.LoanRepayment;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.service.PostingService.Posting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Takes a loan's due installments from the member's account (owner decisions 2026-11-24):
 * - installments fall due on the payout day of each month;
 * - whatever the account holds is taken, even if that empties it; what can't be taken stays
 *   OVERDUE and is tried again every day until it's cleared. No penalty;
 * - oldest installment first; each amount is split between borrowed money and interest in the
 *   installment's own ratio, for the ledger only;
 * - the member is texted what was taken, what's left on that installment and on the whole loan.
 *
 * One loan per database transaction. Each installment is tried at most once a day: the
 * transaction's idempotency key includes the date, so running the job again the same day, or on
 * two servers at once, can't take money twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoanRepaymentService {

    private final LoanAccountRepo loanAccountRepo;

    private final AmortizationScheduleRepo scheduleRepo;

    private final LoanRepaymentRepo repaymentRepo;

    private final MemberAccountRepo memberAccountRepo;

    private final TransactionRepo transactionRepo;

    private final TransactionService transactionService;

    private final PostingService postingService;

    private final TransactionSmsService transactionSmsService;

    @Transactional
    public void collect(UUID loanId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LoanAccount loan = loanAccountRepo.lockById(loanId).orElse(null);
        if (loan == null || loan.getStatus() != LoanAccountStatus.ACTIVE) {
            return;
        }
        List<AmortizationSchedule> due = scheduleRepo.findDueUnpaid(loanId, today);
        if (due.isEmpty()) {
            return;
        }
        // Same lock as office withdrawals and deposits, so the balance read here can't change underneath.
        MemberAccount account = memberAccountRepo.lockById(loan.getMemberAccount().getId()).orElseThrow();
        boolean canTake = account.getStatus() == AccountStatus.ACTIVE;
        if (!canTake) {
            log.warn("Loan {}: account {} is {}; installments marked overdue, nothing taken", loanId,
                    TransactionSmsService.mask(account), account.getStatus());
        }

        for (AmortizationSchedule installment : due) {
            String key = "loan-installment-" + installment.getId() + "-" + today;
            BigDecimal available = account.getAvailableBalance().max(BigDecimal.ZERO);
            BigDecimal take = canTake ? installment.remaining().min(available) : BigDecimal.ZERO;
            if (take.signum() > 0 && transactionRepo.findByIdempotencyKey(key).isEmpty()) {
                takeInstallment(loan, installment, account, take, key, now);
            }
            if (installment.remaining().signum() == 0) {
                installment.setStatus(ScheduleStatus.PAID);
                installment.setPaidAt(now);
            } else {
                installment.setStatus(ScheduleStatus.OVERDUE);
            }
        }

        List<AmortizationSchedule> all = scheduleRepo.findByLoanAccount_IdOrderByInstallmentNumberAsc(loanId);
        if (all.stream().allMatch(i -> i.getStatus() == ScheduleStatus.PAID)) {
            loan.setStatus(LoanAccountStatus.CLOSED_PAID);
            loan.setClosedAt(now);
            log.info("Loan {} fully repaid", loanId);
        }
    }

    private void takeInstallment(LoanAccount loan, AmortizationSchedule installment, MemberAccount account,
                                 BigDecimal take, String key, LocalDateTime now) {
        LoanRules.Split split = LoanRules.split(installment, take);
        account.setCurrentBalance(account.getCurrentBalance().subtract(take));
        account.setAvailableBalance(account.getAvailableBalance().subtract(take));
        Transaction transaction = transactionService.record(Transaction.builder()
                .idempotencyKey(key)
                .transactionType(TransactionType.LOAN_PAYMENT)
                .amount(take)
                .memberAccount(account)
                .description("Installment " + installment.getInstallmentNumber() + " of " + loan.getTermMonths())
                .status(TransactionStatus.COMPLETED)
                .channel(TransactionChannel.SYSTEM)
                .createdAt(now)
                .postedAt(now)
                .build());
        postingService.post(transaction, Posting.of(PostingEvent.LOAN_REPAYMENT, account.getAccountType(), null)
                .with(AmountPart.PRINCIPAL, take)
                .with(AmountPart.LOAN_PRINCIPAL, split.principal())
                .with(AmountPart.INTEREST, split.interest()), now);
        repaymentRepo.save(LoanRepayment.builder()
                .loanAccount(loan)
                .installment(installment)
                .transaction(transaction)
                .principalPart(split.principal())
                .interestPart(split.interest())
                .createdAt(now)
                .build());
        installment.setPaidPrincipal(installment.getPaidPrincipal().add(split.principal()));
        installment.setPaidInterest(installment.getPaidInterest().add(split.interest()));

        BigDecimal loanLeft = scheduleRepo.findByLoanAccount_IdOrderByInstallmentNumberAsc(loan.getId()).stream()
                .map(AmortizationSchedule::remaining)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        transactionSmsService.loanInstallment(transaction, account, installment, loan.getTermMonths(), loanLeft);
    }
}
