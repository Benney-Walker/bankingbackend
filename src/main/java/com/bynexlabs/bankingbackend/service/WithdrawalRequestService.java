package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorCorrectionRequest;
import com.bynexlabs.bankingbackend.DTO.collector.WithdrawalRequestCreate;
import com.bynexlabs.bankingbackend.DTO.collector.WithdrawalRequestResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionRequest;
import com.bynexlabs.bankingbackend.Repository.CollectorCorrectionRepo;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Repository.WithdrawalRequestRepo;
import com.bynexlabs.bankingbackend.Utility.CollectorRules;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.CorrectionRecordType;
import com.bynexlabs.bankingbackend.enums.WithdrawalRequestStatus;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.InternalServerError;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.CollectorCorrection;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.WithdrawalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Withdrawal requests (owner decisions 2026-11-07 and 2026-11-14). Collectors can't withdraw,
 * but they can create a request for a member in their branch, from the member's savings
 * account. Creating it never moves money.
 *
 * The collector can correct it for 10 minutes, like a payment. After that an administrator or
 * agent approves or rejects it (reason required), and the member collects the cash at the
 * office: paying the request records a normal withdrawal, with the fee, minimum balance and
 * daily limit checked at that moment and the member texted.
 *
 * Agents only see, review and pay requests for members of their own branch; administrators
 * act on every branch (owner decision 2026-11-14).
 */
@Service
@RequiredArgsConstructor
public class WithdrawalRequestService {

    public static final int MAX_PAGE_SIZE = 100;

    private final WithdrawalRequestRepo requestRepo;

    private final MemberAccountRepo memberAccountRepo;

    private final CollectorCorrectionRepo correctionRepo;

    private final MoneyMovementService moneyMovementService;

    private final TransactionRepo transactionRepo;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    // ------------------------------------------------------------------ collectors

    @Transactional
    public WithdrawalRequestResponse create(WithdrawalRequestCreate request, Staff collector) {
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());
        LocalDateTime now = LocalDateTime.now(clock);
        CollectorRules.requireValidRecordedAt(request.getRecordedAt(), now);

        Optional<WithdrawalRequest> existing = requestRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            if (!existing.get().getRequestedBy().getId().equals(collector.getId())) {
                throw new ConflictException("This idempotency key was already used for a different request");
            }
            return response(existing.get());
        }

        MemberAccount account = findSavingsAccount(request.getAccountNumber());
        CollectorRules.requireSameBranch(collector, account);
        CollectorRules.requireActive(account);

        WithdrawalRequest saved = requestRepo.save(WithdrawalRequest.builder()
                .idempotencyKey(key)
                .memberAccount(account)
                .amount(amount)
                .description(request.getDescription() == null || request.getDescription().isBlank()
                        ? null : request.getDescription().trim())
                .status(WithdrawalRequestStatus.PENDING)
                .requestedBy(collector)
                .createdAt(now)
                .recordedAt(request.getRecordedAt())
                .build());
        return response(saved);
    }

    /** Amount and/or the member's savings account, within 10 minutes, by the requesting collector. */
    @Transactional
    public WithdrawalRequestResponse correct(UUID requestId, CollectorCorrectionRequest request, Staff collector) {
        WithdrawalRequest withdrawal = lock(requestId);
        if (!withdrawal.getRequestedBy().getId().equals(collector.getId())) {
            throw new ForbiddenException("You can only correct requests you created");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (withdrawal.getStatus() != WithdrawalRequestStatus.PENDING
                || !CollectorRules.windowOpen(withdrawal.getCreatedAt(), withdrawal.getRecordedAt(), now)) {
            throw new ConflictException("The 10-minute correction window for this request has closed");
        }

        BigDecimal newAmount = request.getAmount() == null
                ? withdrawal.getAmount() : MoneyRules.requireValidAmount(request.getAmount());
        MemberAccount current = withdrawal.getMemberAccount();
        MemberAccount newAccount = current;
        if (request.getAccountNumber() != null && !request.getAccountNumber().trim().equals(current.getAccountNumber())) {
            newAccount = findSavingsAccount(request.getAccountNumber());
            if (!newAccount.getMember().getId().equals(current.getMember().getId())) {
                throw new BadRequestException("A request can only move to another account of the same member");
            }
            CollectorRules.requireActive(newAccount);
        }
        if (newAmount.compareTo(withdrawal.getAmount()) == 0 && newAccount == current) {
            return response(withdrawal);
        }

        correctionRepo.save(CollectorCorrection.builder()
                .recordType(CorrectionRecordType.WITHDRAWAL_REQUEST)
                .recordId(withdrawal.getId())
                .previousAmount(withdrawal.getAmount())
                .previousAccount(current)
                .newAmount(newAmount)
                .newAccount(newAccount)
                .correctedBy(collector)
                .correctedAt(now)
                .build());
        withdrawal.setAmount(newAmount);
        withdrawal.setMemberAccount(newAccount);
        return response(withdrawal);
    }

    /** The collector's own requests, newest first, so the app can show each outcome. */
    @Transactional(readOnly = true)
    public PagedModel<WithdrawalRequestResponse> ownRequests(Staff collector, int page, int size) {
        return new PagedModel<>(requestRepo.findByRequestedBy_Id(collector.getId(), pageRequest(page, size))
                .map(this::response));
    }

    // ------------------------------------------------------------------ office

    /** Administrators see every branch's requests; agents only their own branch's. */
    @Transactional(readOnly = true)
    public PagedModel<WithdrawalRequestResponse> list(WithdrawalRequestStatus status, int page, int size,
                                                      Staff reviewer, boolean reviewerIsAdmin) {
        WithdrawalRequestStatus wanted = status == null ? WithdrawalRequestStatus.PENDING : status;
        PageRequest pageRequest = pageRequest(page, size);
        if (reviewerIsAdmin) {
            return new PagedModel<>(requestRepo.findByStatus(wanted, pageRequest).map(this::response));
        }
        if (reviewer.getBranch() == null) {
            throw new ForbiddenException("You are not assigned to a branch");
        }
        return new PagedModel<>(requestRepo.findByStatusAndMemberAccount_Member_Branch_Id(
                wanted, reviewer.getBranch().getId(), pageRequest).map(this::response));
    }

    @Transactional
    public WithdrawalRequestResponse approve(UUID requestId, Staff reviewer, boolean reviewerIsAdmin) {
        WithdrawalRequest withdrawal = lockForReview(requestId, reviewer, reviewerIsAdmin);
        withdrawal.setStatus(WithdrawalRequestStatus.APPROVED);
        withdrawal.setReviewedBy(reviewer);
        withdrawal.setReviewedAt(LocalDateTime.now(clock));
        return response(withdrawal);
    }

    @Transactional
    public WithdrawalRequestResponse reject(UUID requestId, String reason, Staff reviewer, boolean reviewerIsAdmin) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("A reason is required");
        }
        WithdrawalRequest withdrawal = lockForReview(requestId, reviewer, reviewerIsAdmin);
        withdrawal.setStatus(WithdrawalRequestStatus.REJECTED);
        withdrawal.setRejectionReason(reason.trim());
        withdrawal.setReviewedBy(reviewer);
        withdrawal.setReviewedAt(LocalDateTime.now(clock));
        return response(withdrawal);
    }

    /**
     * The member collects the cash at the office (owner decision 2026-11-14): records the
     * withdrawal through MoneyMovementService, in the same database transaction, and marks the
     * request PAID. If the withdrawal is refused (balance, daily limit), the request stays APPROVED.
     */
    @Transactional
    public WithdrawalRequestResponse pay(UUID requestId, String idempotencyKey, Staff cashier, boolean cashierIsAdmin) {
        WithdrawalRequest withdrawal = lock(requestId);
        requireReviewerBranch(withdrawal, cashier, cashierIsAdmin);
        if (withdrawal.getStatus() == WithdrawalRequestStatus.PAID) {
            // A retried payment with the same key returns the request; anything else is a mistake.
            if (withdrawal.getTransaction().getIdempotencyKey().equals(idempotencyKey)) {
                return response(withdrawal);
            }
            throw new ConflictException("This request has already been paid");
        }
        if (withdrawal.getStatus() != WithdrawalRequestStatus.APPROVED) {
            throw new ConflictException("Only approved requests can be paid; this one is " + withdrawal.getStatus());
        }

        moneyMovementService.withdraw(TransactionRequest.builder()
                .idempotencyKey(idempotencyKey)
                .accountNumber(withdrawal.getMemberAccount().getAccountNumber())
                .amount(withdrawal.getAmount())
                .description("Withdrawal request " + withdrawal.getId())
                .build(), cashier);
        withdrawal.setTransaction(transactionRepo.findByIdempotencyKey(idempotencyKey.trim())
                .orElseThrow(() -> new InternalServerError("The withdrawal for this request wasn't recorded")));
        withdrawal.setStatus(WithdrawalRequestStatus.PAID);
        return response(withdrawal);
    }

    // ------------------------------------------------------------------ helpers

    /** A request can be reviewed once it's final: its correction window has passed. */
    private WithdrawalRequest lockForReview(UUID requestId, Staff reviewer, boolean reviewerIsAdmin) {
        WithdrawalRequest withdrawal = lock(requestId);
        requireReviewerBranch(withdrawal, reviewer, reviewerIsAdmin);
        if (withdrawal.getStatus() != WithdrawalRequestStatus.PENDING) {
            throw new ConflictException("This request is already " + withdrawal.getStatus());
        }
        LocalDateTime until = CollectorRules.correctableUntil(withdrawal.getCreatedAt(), withdrawal.getRecordedAt());
        if (until != null && LocalDateTime.now(clock).isBefore(until)) {
            throw new ConflictException("The collector can still correct this request until " + until.toLocalTime().withNano(0));
        }
        return withdrawal;
    }

    // Owner decision 2026-11-14: agents act only on their own branch's requests.
    private static void requireReviewerBranch(WithdrawalRequest withdrawal, Staff reviewer, boolean reviewerIsAdmin) {
        if (reviewerIsAdmin) {
            return;
        }
        if (reviewer.getBranch() == null
                || !reviewer.getBranch().getId().equals(withdrawal.getMemberAccount().getMember().getBranch().getId())) {
            throw new ForbiddenException("This request is from another branch");
        }
    }

    private WithdrawalRequest lock(UUID requestId) {
        return requestRepo.lockById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Withdrawal request not found"));
    }

    private MemberAccount findSavingsAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BadRequestException("Account number is required");
        }
        MemberAccount account = memberAccountRepo.findWithMemberByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getAccountType() != AccountType.SAVINGS) {
            throw new BadRequestException("Withdrawal requests are only for savings accounts");
        }
        return account;
    }

    private WithdrawalRequestResponse response(WithdrawalRequest withdrawal) {
        LocalDateTime until = withdrawal.getStatus() == WithdrawalRequestStatus.PENDING
                ? CollectorRules.correctableUntil(withdrawal.getCreatedAt(), withdrawal.getRecordedAt()) : null;
        if (until != null && !LocalDateTime.now(clock).isBefore(until)) {
            until = null;
        }
        return WithdrawalRequestResponse.from(withdrawal, until);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
