package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.TransactionSmsEvent;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Writes the member SMS for every transaction (owner decision 2026-11-14) and queues it through
 * the outbox, inside the transaction's own database transaction. So the message:
 * - shows the balance right after this transaction;
 * - is only sent if the transaction commits, never for a failed or rolled-back one;
 * - is sent by TransactionSmsSender in the background, with retries, so a slow SMS provider
 *   never holds the account locks or delays the counter.
 *
 * Wording approved 2026-11-14: detailed, account numbers masked to the last 4 digits, no name
 * prefix (the Vynfy sender ID identifies the credit union). Each message ends with a reference,
 * the first 8 characters of the transaction ID.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionSmsService {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OutboxService outboxService;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deposit(Transaction transaction, MemberAccount account) {
        send(transaction, account, money(transaction.getAmount()) + " deposited to your " + describe(account)
                + " on " + when(transaction) + ". New balance: " + money(account.getCurrentBalance()) + ref(transaction));
    }

    /** First deposit split between the chosen account and the shares account. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void firstDeposit(Transaction transaction, MemberAccount account, MemberAccount sharesAccount) {
        BigDecimal shares = transaction.getShareAmount();
        if (shares == null || shares.signum() == 0) {
            deposit(transaction, account);
            return;
        }
        BigDecimal rest = transaction.getAmount().subtract(shares);
        String type = account.getAccountType().name();
        send(transaction, account, money(transaction.getAmount()) + " deposited on " + when(transaction) + ": "
                + money(shares) + " to shares " + mask(sharesAccount) + ", " + money(rest) + " to " + type + " " + mask(account)
                + ". Balances: shares " + money(sharesAccount.getCurrentBalance()) + ", " + type + " "
                + money(account.getCurrentBalance()) + ref(transaction));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void sharePurchase(Transaction transaction, MemberAccount sharesAccount) {
        send(transaction, sharesAccount, money(transaction.getAmount()) + " paid into your " + describe(sharesAccount)
                + " on " + when(transaction) + ". New balance: " + money(sharesAccount.getCurrentBalance()) + ref(transaction));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void withdrawal(Transaction transaction, MemberAccount account) {
        BigDecimal fee = transaction.getFeeAmount();
        String feePart = fee == null || fee.signum() == 0 ? "" : " Fee: " + money(fee) + ".";
        send(transaction, account, money(transaction.getAmount()) + " withdrawn from your " + describe(account)
                + " on " + when(transaction) + "." + feePart + " New balance: " + money(account.getCurrentBalance())
                + ref(transaction));
    }

    /**
     * A transfer: one SMS when both accounts belong to the same member, otherwise one to the
     * sender and one to the receiver. Neither sees the other member's name.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void transfer(Transaction transaction, MemberAccount from, MemberAccount to) {
        String amount = money(transaction.getAmount());
        if (from.getMember().getId().equals(to.getMember().getId())) {
            send(transaction, from, amount + " moved from your " + describe(from) + " to your " + describe(to)
                    + " on " + when(transaction) + ". Balances: " + from.getAccountType().name() + " "
                    + money(from.getCurrentBalance()) + ", " + to.getAccountType().name() + " "
                    + money(to.getCurrentBalance()) + ref(transaction));
            return;
        }
        send(transaction, from, amount + " transferred from your " + describe(from) + " to account " + mask(to)
                + " on " + when(transaction) + ". New balance: " + money(from.getCurrentBalance()) + ref(transaction));
        send(transaction, to, amount + " received in your " + describe(to) + " from account " + mask(from)
                + " on " + when(transaction) + ". New balance: " + money(to.getCurrentBalance()) + ref(transaction));
    }

    private void send(Transaction transaction, MemberAccount account, String message) {
        String phone = account.getMember().getPhoneNumber();
        if (phone == null || phone.isBlank()) {
            // Members always have a phone (registration requires one); don't fail the money movement over it.
            log.warn("No phone number for the owner of account {}; transaction SMS {} not sent",
                    mask(account), transaction.getId());
            return;
        }
        outboxService.enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.TRANSACTION_SMS_ROUTING_KEY,
                TransactionSmsEvent.builder()
                        .transactionId(transaction.getId())
                        .phoneNumber(phone)
                        .message(message)
                        .build());
    }

    /** "SAVINGS account ****0002", or "shares account ****0001". */
    private static String describe(MemberAccount account) {
        String type = account.getAccountType() == AccountType.SHARES ? "shares" : account.getAccountType().name();
        return type + " account " + mask(account);
    }

    static String mask(MemberAccount account) {
        String number = account.getAccountNumber();
        return "****" + (number.length() <= 4 ? number : number.substring(number.length() - 4));
    }

    static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return "GHS " + format.format(amount);
    }

    // When the money changed hands: the device time of an offline record, otherwise when it was
    // recorded (for a collector payment, before the 10-minute window), otherwise when posted.
    private static String when(Transaction transaction) {
        LocalDateTime time = transaction.getRecordedAt() != null ? transaction.getRecordedAt()
                : transaction.getCreatedAt() != null ? transaction.getCreatedAt() : transaction.getPostedAt();
        return time.format(WHEN);
    }

    private static String ref(Transaction transaction) {
        return ". Ref: " + transaction.getId().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }
}
