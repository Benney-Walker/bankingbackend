package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorCorrectionRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorDaySummary;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorMemberResponse;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentResponse;
import com.bynexlabs.bankingbackend.Repository.CollectorCorrectionRepo;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.CollectorRules;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.CollectorPaymentType;
import com.bynexlabs.bankingbackend.enums.CorrectionRecordType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.CollectorCorrection;
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
 * - saved straight away as PENDING; the collector can correct the amount or move it to the
 *   member's other account for 10 minutes; then CollectorPaymentFinalizer applies it (balance,
 *   ledger, member SMS). Offline records arrive final and are applied on arrival.
 */
@Service
@RequiredArgsConstructor
public class CollectorPaymentService {

    private static final EnumSet<AccountType> COLLECTOR_ACCOUNT_TYPES = EnumSet.of(AccountType.SAVINGS, AccountType.SHARES);

    private final TransactionRepo transactionRepo;

    private final TransactionService transactionService;

    private final MemberAccountRepo memberAccountRepo;

    private final CollectorCorrectionRepo correctionRepo;

    private final CollectorPaymentApplier applier;

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
        CollectorRules.requireValidRecordedAt(request.getRecordedAt(), now);

        // A retry or offline re-sync of a payment already saved returns it as it is now, even if
        // the collector corrected it since.
        Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            return response(requireOwnPayment(existing.get(), collector, true));
        }

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
                .createdAt(now)
                .recordedAt(request.getRecordedAt())
                .build());

        // D40: an offline record is sent only after its 10 minutes, so it's final already.
        if (request.getRecordedAt() != null) {
            applier.apply(transaction);
        }
        return response(transaction);
    }

    /**
     * Changes the amount and/or moves the payment to the member's other collector account
     * (savings or shares), within 10 minutes of saving and only by the collector who recorded it.
     * Each change is kept as a CollectorCorrection.
     */
    @Transactional
    public CollectorPaymentResponse correct(UUID transactionId, CollectorCorrectionRequest request, Staff collector) {
        Transaction transaction = transactionRepo.lockById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        requireOwnPayment(transaction, collector, false);
        LocalDateTime now = LocalDateTime.now(clock);
        if (transaction.getStatus() != TransactionStatus.PENDING
                || !CollectorRules.windowOpen(transaction.getCreatedAt(), transaction.getRecordedAt(), now)) {
            throw new ConflictException("The 10-minute correction window for this payment has closed");
        }

        BigDecimal newAmount = request.getAmount() == null
                ? transaction.getAmount() : MoneyRules.requireValidAmount(request.getAmount());
        MemberAccount current = transaction.getMemberAccount();
        MemberAccount newAccount = current;
        if (request.getAccountNumber() != null && !request.getAccountNumber().trim().equals(current.getAccountNumber())) {
            newAccount = findAccount(request.getAccountNumber());
            if (!newAccount.getMember().getId().equals(current.getMember().getId())) {
                throw new BadRequestException("A payment can only move to another account of the same member");
            }
            if (!COLLECTOR_ACCOUNT_TYPES.contains(newAccount.getAccountType())) {
                throw new BadRequestException("Collectors record only into savings or shares accounts");
            }
            CollectorRules.requireActive(newAccount);
        }
        if (newAmount.compareTo(transaction.getAmount()) == 0 && newAccount == current) {
            return response(transaction);
        }

        correctionRepo.save(CollectorCorrection.builder()
                .recordType(CorrectionRecordType.PAYMENT)
                .recordId(transaction.getId())
                .previousAmount(transaction.getAmount())
                .previousAccount(current)
                .newAmount(newAmount)
                .newAccount(newAccount)
                .correctedBy(collector)
                .correctedAt(now)
                .build());
        transaction.setAmount(newAmount);
        transaction.setMemberAccount(newAccount);
        transaction.setTransactionType(newAccount.getAccountType() == AccountType.SHARES
                ? TransactionType.SHARES : TransactionType.DEPOSIT);
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
        LocalDateTime until = transaction.getStatus() == TransactionStatus.PENDING
                ? CollectorRules.correctableUntil(transaction.getCreatedAt(), transaction.getRecordedAt()) : null;
        return CollectorPaymentResponse.from(transaction, until);
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

    private static Transaction requireOwnPayment(Transaction transaction, Staff collector, boolean forReplay) {
        boolean own = transaction.getChannel() == TransactionChannel.COLLECTOR
                && transaction.getInitiatedBy() != null
                && transaction.getInitiatedBy().getId().equals(collector.getId());
        if (!own) {
            if (forReplay) {
                throw new ConflictException("This idempotency key was already used for a different transaction");
            }
            throw new ForbiddenException("You can only correct payments you recorded");
        }
        return transaction;
    }

    private static TransactionType typeFor(CollectorPaymentType type) {
        return type == CollectorPaymentType.SHARES ? TransactionType.SHARES : TransactionType.DEPOSIT;
    }
}
