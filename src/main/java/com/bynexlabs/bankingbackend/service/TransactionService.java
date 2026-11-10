package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Transaction groundwork (roadmap 5.1): idempotency for every money movement. No deposit,
 * withdrawal or transfer is offered yet; those wait for the approved chart of accounts and
 * posting rules (D15, D30). Recorded transactions are final: no reversals (D16).
 *
 * Intended flow for each money movement, in one database transaction:
 * 1. AccountLockService.lock... - lock the account(s);
 * 2. findReplay(key, ...) - if the same request was already recorded, return that result.
 *    Checking after the lock means a simultaneous retry waits for the first attempt to
 *    commit and then sees its transaction, instead of failing on the unique key;
 * 3. check balances, update them, post journal lines, record(transaction) with the
 *    trimmed key from requireValidKey.
 */
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepo transactionRepo;

    /**
     * The same idempotency key with the same request is a retry: the original transaction is
     * returned and nothing is recorded again. The same key with a different request is an error.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Transaction> findReplay(String idempotencyKey, TransactionType type,
                                            String accountNumber, BigDecimal amount) {
        String key = requireValidKey(idempotencyKey);
        Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(key);
        existing.ifPresent(transaction -> {
            boolean sameRequest = transaction.getTransactionType() == type
                    && transaction.getAmount().compareTo(amount) == 0
                    && transaction.getMemberAccount() != null
                    && transaction.getMemberAccount().getAccountNumber().equals(accountNumber);
            if (!sameRequest) {
                throw new ConflictException("This idempotency key was already used for a different transaction");
            }
        });
        return existing;
    }

    /**
     * Saves a new transaction. If a simultaneous retry with the same key got there first, the
     * unique constraint on idempotency_key rejects this one, and the caller's whole database
     * transaction (including any balance change) rolls back.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Transaction record(Transaction transaction) {
        try {
            return transactionRepo.saveAndFlush(transaction);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("A transaction with this idempotency key is already being processed");
        }
    }

    static String requireValidKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BadRequestException("Idempotency key is required");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 100) {
            throw new BadRequestException("Idempotency key must be at most 100 characters");
        }
        return key;
    }
}
