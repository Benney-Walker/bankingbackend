package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorDaySummary;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorMemberResponse;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentResponse;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.CollectorRules;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.CollectorPaymentType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Payments collectors take in the field (owner decisions 2026-11-07 and 2026-11-14):
 * - two kinds: SAVINGS (a deposit into the member's savings account) and SHARES (a share
 *   purchase into their shares account);
 * - any member in the collector's branch, but only after the member's first deposit, which
 *   must be made at the office;
 * - final on arrival (owner decision 2026-11-23): the app keeps a new payment for 5 minutes, during
 *   which the collector can correct it in the app, then sends it with the phone's createdAt and
 *   updatedAt. The backend applies it straight away (balance, ledger, member SMS) and notes when it
 *   received it. The phone's createdAt decides the collector's day.
 */
@Service
@RequiredArgsConstructor
public class CollectorPaymentService {

    private static final EnumSet<AccountType> COLLECTOR_ACCOUNT_TYPES = EnumSet.of(AccountType.SAVINGS, AccountType.SHARES);

    private final TransactionRepo transactionRepo;

    private final TransactionService transactionService;

    private final MemberAccountRepo memberAccountRepo;

    private final CollectorPaymentApplier applier;

    private final CollectorSubmissionService submissionService;

    private final CollectorLocationService locationService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** The members a collector can record for, with their savings and shares accounts. */
    @Transactional(readOnly = true)
    public List<CollectorMemberResponse> members(Staff collector) {
        Map<UUID, String> names = new LinkedHashMap<>();
        Map<UUID, List<CollectorMemberResponse.Account>> accounts = new LinkedHashMap<>();
        for (MemberAccount account : memberAccountRepo.findForBranch(collector.getBranch().getId(),
                MembershipStatus.ACTIVE, AccountStatus.ACTIVE, COLLECTOR_ACCOUNT_TYPES)) {
            UUID memberId = account.getMember().getId();
            names.putIfAbsent(memberId, account.getMember().getFirstName() + " " + account.getMember().getLastName());
            accounts.computeIfAbsent(memberId, id -> new ArrayList<>())
                    .add(new CollectorMemberResponse.Account(account.getAccountNumber(), account.getAccountType()));
        }
        return names.entrySet().stream()
                .map(member -> new CollectorMemberResponse(member.getValue(), accounts.get(member.getKey())))
                .toList();
    }

    @Transactional
    public CollectorPaymentResponse record(CollectorPaymentRequest request, Staff collector) {
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());
        LocalDateTime now = LocalDateTime.now(clock);
        CollectorRules.requireValidPhoneTimes(request.getCreatedAt(), request.getUpdatedAt(), now);

        // A retry or offline re-sync of a payment already saved returns it as it is. The key lock
        // makes a simultaneous duplicate wait for the first request and then get its payment,
        // instead of a unique-key 409.
        transactionRepo.lockIdempotencyKey(key);
        Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Transaction payment = requireOwnPayment(existing.get(), collector);
            requireSamePayment(payment, request, amount);
            return response(payment);
        }
        // Once the collector has submitted for the day, nothing more can be recorded.
        submissionService.requireCanRecord(collector);
        // A payment from a day already handed in can't be added afterwards.
        submissionService.requireCreatedAfterLastSubmission(collector, request.getCreatedAt());
        // Owner decision 2026-11-23: no location near the time of the payment, no payment.
        locationService.requireRecentLocation(collector, request.getCreatedAt());

        MemberAccount account = findAccount(request.getAccountNumber());
        requireAccountForType(account, request.getType());
        CollectorRules.requireSameBranch(collector, account);
        CollectorRules.requireActive(account);
        requireFirstDepositMade(account);

        Transaction transaction = transactionService.record(Transaction.builder()
                .idempotencyKey(key)
                .transactionType(typeFor(request.getType()))
                .amount(amount)
                .memberAccount(account)
                .initiatedBy(collector)
                .description(request.getDescription() == null || request.getDescription().isBlank()
                        ? null : request.getDescription().trim())
                .status(TransactionStatus.PENDING)
                .channel(TransactionChannel.COLLECTOR)
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .receivedAt(now)
                .build());

        // Final on arrival: applied now, COMPLETED or FAILED with the reason.
        applier.apply(transaction);
        return response(transaction);
    }

    /** The collector's own payments on a day (default today), newest first, with totals. */
    @Transactional(readOnly = true)
    public CollectorDaySummary daySummary(Staff collector, LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        List<CollectorPaymentResponse> payments = transactionRepo
                .findByInitiatedBy_IdAndChannelAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                        collector.getId(), TransactionChannel.COLLECTOR, day.atStartOfDay(), day.plusDays(1).atStartOfDay())
                .stream().map(this::response).toList();

        BigDecimal savings = BigDecimal.ZERO;
        BigDecimal shares = BigDecimal.ZERO;
        for (CollectorPaymentResponse payment : payments) {
            if (payment.status() == TransactionStatus.FAILED) {
                continue;
            }
            if (payment.type() == CollectorPaymentType.SHARES) {
                shares = shares.add(payment.amount());
            } else {
                savings = savings.add(payment.amount());
            }
        }
        return new CollectorDaySummary(day, payments.size(), savings, shares, savings.add(shares), payments);
    }

    private CollectorPaymentResponse response(Transaction transaction) {
        return CollectorPaymentResponse.from(transaction);
    }

    private MemberAccount findAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BadRequestException("Account number is required");
        }
        return memberAccountRepo.findWithMemberByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static void requireAccountForType(MemberAccount account, CollectorPaymentType type) {
        AccountType expected = type == CollectorPaymentType.SHARES ? AccountType.SHARES : AccountType.SAVINGS;
        if (account.getAccountType() != expected) {
            throw new BadRequestException(type == CollectorPaymentType.SHARES
                    ? "Share purchases go to the member's shares account"
                    : "Savings deposits go to the member's savings account");
        }
    }

    // Owner decision 2026-11-14: a member's first deposit is made at the office only.
    private void requireFirstDepositMade(MemberAccount account) {
        if (!transactionRepo.existsByMemberAccount_Member_IdAndTransactionTypeAndStatus(
                account.getMember().getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED)) {
            throw new ConflictException("This member's first deposit must be made at the office");
        }
    }

    /**
     * A re-sent key must describe the same payment. Otherwise the key was reused for a different
     * payment, which must not be silently swallowed.
     */
    private static void requireSamePayment(Transaction payment, CollectorPaymentRequest request, BigDecimal amount) {
        String accountNumber = request.getAccountNumber() == null ? null : request.getAccountNumber().trim();
        boolean same = payment.getTransactionType() == typeFor(request.getType())
                && payment.getAmount().compareTo(amount) == 0
                && payment.getMemberAccount().getAccountNumber().equals(accountNumber);
        if (!same) {
            throw new ConflictException("This idempotency key was already used for a different transaction");
        }
    }

    private static Transaction requireOwnPayment(Transaction transaction, Staff collector) {
        boolean own = transaction.getChannel() == TransactionChannel.COLLECTOR
                && transaction.getInitiatedBy() != null
                && transaction.getInitiatedBy().getId().equals(collector.getId());
        if (!own) {
            throw new ConflictException("This idempotency key was already used for a different transaction");
        }
        return transaction;
    }

    private static TransactionType typeFor(CollectorPaymentType type) {
        return type == CollectorPaymentType.SHARES ? TransactionType.SHARES : TransactionType.DEPOSIT;
    }
}
