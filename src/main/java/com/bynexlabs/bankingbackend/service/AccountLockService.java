package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Locks member accounts before their balances change (roadmap 5.1). Uses PostgreSQL row locks
 * (PESSIMISTIC_WRITE, i.e. SELECT ... FOR UPDATE): concurrent money movements on the same
 * account run one at a time, so a balance check can't be overtaken by another request.
 *
 * Rules for callers:
 * - call inside the money-movement transaction (MANDATORY); the locks are released at commit;
 * - lock before reading any balance, and keep the transaction short;
 * - never call SMS, storage or other external services while holding these locks.
 */
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final MemberAccountRepo memberAccountRepo;

    /** Locks one account and checks that both it and its member can transact. */
    @Transactional(propagation = Propagation.MANDATORY)
    public MemberAccount lockForTransaction(String accountNumber) {
        MemberAccount account = memberAccountRepo.lockById(resolveId(accountNumber))
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        requireCanTransact(account);
        return account;
    }

    /**
     * Locks both accounts of a transfer. Locks are always taken in ascending account-ID order,
     * whichever direction the money goes, so two opposite transfers between the same accounts
     * can't each hold one lock and wait forever for the other (deadlock).
     *
     * @return [from, to]
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<MemberAccount> lockForTransfer(String fromAccountNumber, String toAccountNumber) {
        UUID fromId = resolveId(fromAccountNumber);
        UUID toId = resolveId(toAccountNumber);
        if (fromId.equals(toId)) {
            throw new BadRequestException("Cannot transfer to the same account");
        }

        boolean fromFirst = fromId.compareTo(toId) < 0;
        MemberAccount first = lock(fromFirst ? fromId : toId);
        MemberAccount second = lock(fromFirst ? toId : fromId);
        MemberAccount from = fromFirst ? first : second;
        MemberAccount to = fromFirst ? second : first;

        requireCanTransact(from);
        requireCanTransact(to);
        return List.of(from, to);
    }

    private MemberAccount lock(UUID accountId) {
        return memberAccountRepo.lockById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private UUID resolveId(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BadRequestException("Account number is required");
        }
        return memberAccountRepo.findIdByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    // Accounts and members only become ACTIVE after KYC verification (confirmed rule), so money
    // can't move on an account before then.
    private static void requireCanTransact(MemberAccount account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new ConflictException("Account " + account.getAccountNumber() + " is " + account.getStatus());
        }
        if (account.getMember().getMembershipStatus() != MembershipStatus.ACTIVE) {
            throw new ConflictException("The member who owns account " + account.getAccountNumber() + " is not active");
        }
    }
}
