package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.ApiException;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.service.PostingService.Posting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Applies a collector payment once it's final (D39): its 10 minutes have passed, or it was
 * recorded offline. Same steps as an office deposit or share purchase, in one database
 * transaction: lock the account, update the balance, post the journal (cash to 1015 Cash with
 * Collectors), mark it COMPLETED and queue the member SMS.
 *
 * The cash is already with the collector, so a business problem (e.g. the account was frozen
 * meanwhile) doesn't throw: the payment is marked FAILED with the reason, for the office to
 * sort out with the collector. A ledger misconfiguration still throws and rolls back, so it's
 * retried once fixed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorPaymentApplier {

    private final TransactionRepo transactionRepo;

    private final AccountLockService accountLockService;

    private final PostingService postingService;

    private final TransactionSmsService transactionSmsService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** For the finaliser: each payment in its own transaction, so one failure doesn't stop the rest. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finaliseDue(UUID transactionId) {
        transactionRepo.lockPendingCollectorPayment(transactionId).ifPresent(this::apply);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void apply(Transaction transaction) {
        if (transaction.getStatus() != TransactionStatus.PENDING) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        MemberAccount account;
        try {
            account = accountLockService.lockForTransaction(transaction.getMemberAccount().getAccountNumber());
        } catch (ApiException e) {
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transaction.setPostedAt(now);
            log.warn("Collector payment {} could not be applied: {}", transaction.getId(), e.getMessage());
            return;
        }

        boolean shares = transaction.getTransactionType() == TransactionType.SHARES;
        account.setCurrentBalance(account.getCurrentBalance().add(transaction.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().add(transaction.getAmount()));
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setPostedAt(now);

        postingService.post(transaction, Posting.of(
                        shares ? PostingEvent.COLLECTOR_SHARE_PURCHASE : PostingEvent.COLLECTOR_DEPOSIT,
                        shares ? AccountType.SHARES : account.getAccountType(), null)
                .with(AmountPart.PRINCIPAL, transaction.getAmount()), now);
        if (shares) {
            transactionSmsService.sharePurchase(transaction, account);
        } else {
            transactionSmsService.deposit(transaction, account);
        }
    }
}
