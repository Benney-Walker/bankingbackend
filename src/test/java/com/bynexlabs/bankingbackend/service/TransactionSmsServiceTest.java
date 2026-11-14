package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.TransactionSmsEvent;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** The wording approved on 2026-11-14 (detailed, last 4 digits, no name prefix). */
@ExtendWith(MockitoExtension.class)
class TransactionSmsServiceTest {

    private static final UUID TRANSACTION_ID = UUID.fromString("3f2a9c1b-0000-4000-8000-000000000001");

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private TransactionSmsService smsService;

    private static Member member(String phone) {
        return Member.builder().id(UUID.randomUUID()).phoneNumber(phone).build();
    }

    private static MemberAccount account(Member member, String number, AccountType type, String balance) {
        return MemberAccount.builder().member(member).accountNumber(number).accountType(type)
                .currentBalance(new BigDecimal(balance)).availableBalance(new BigDecimal(balance)).build();
    }

    private static Transaction transaction(String amount) {
        return Transaction.builder().id(TRANSACTION_ID).amount(new BigDecimal(amount))
                .postedAt(LocalDateTime.of(2026, 11, 14, 10, 0)).build();
    }

    private List<TransactionSmsEvent> sent(int count) {
        ArgumentCaptor<TransactionSmsEvent> events = ArgumentCaptor.forClass(TransactionSmsEvent.class);
        verify(outboxService, times(count)).enqueue(eq(RabbitMQConfig.EXCHANGE_NAME),
                eq(RabbitMQConfig.TRANSACTION_SMS_ROUTING_KEY), events.capture());
        return events.getAllValues();
    }

    @Test
    void depositMessage() {
        Member member = member("0241234567");
        smsService.deposit(transaction("40"), account(member, "1000000002", AccountType.SAVINGS, "140"));

        TransactionSmsEvent event = sent(1).get(0);
        assertThat(event.getPhoneNumber()).isEqualTo("0241234567");
        assertThat(event.getTransactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(event.getMessage()).isEqualTo(
                "GHS 40.00 deposited to your SAVINGS account ****0002 on 14/11/2026 10:00. New balance: GHS 140.00. Ref: 3F2A9C1B");
    }

    @Test
    void firstDepositMessageShowsTheSplit() {
        Member member = member("0241234567");
        Transaction transaction = transaction("120");
        transaction.setShareAmount(new BigDecimal("50"));

        smsService.firstDeposit(transaction, account(member, "1000000002", AccountType.CURRENT, "70"),
                account(member, "1000000001", AccountType.SHARES, "50"));

        assertThat(sent(1).get(0).getMessage()).isEqualTo(
                "GHS 120.00 deposited on 14/11/2026 10:00: GHS 50.00 to shares ****0001, GHS 70.00 to CURRENT ****0002. "
                        + "Balances: shares GHS 50.00, CURRENT GHS 70.00. Ref: 3F2A9C1B");
    }

    @Test
    void withdrawalMessageShowsTheFee() {
        Transaction transaction = transaction("100");
        transaction.setFeeAmount(new BigDecimal("2"));

        smsService.withdrawal(transaction, account(member("0241234567"), "1000000002", AccountType.SAVINGS, "38"));

        assertThat(sent(1).get(0).getMessage()).isEqualTo(
                "GHS 100.00 withdrawn from your SAVINGS account ****0002 on 14/11/2026 10:00. Fee: GHS 2.00. New balance: GHS 38.00. Ref: 3F2A9C1B");
    }

    @Test
    void withdrawalWithoutAFeeLeavesTheFeeOut() {
        Transaction transaction = transaction("100");
        transaction.setFeeAmount(BigDecimal.ZERO);

        smsService.withdrawal(transaction, account(member("0241234567"), "1000000002", AccountType.SAVINGS, "40"));

        assertThat(sent(1).get(0).getMessage()).isEqualTo(
                "GHS 100.00 withdrawn from your SAVINGS account ****0002 on 14/11/2026 10:00. New balance: GHS 40.00. Ref: 3F2A9C1B");
    }

    @Test
    void sharePurchaseMessage() {
        smsService.sharePurchase(transaction("25"), account(member("0241234567"), "1000000001", AccountType.SHARES, "75"));

        assertThat(sent(1).get(0).getMessage()).isEqualTo(
                "GHS 25.00 paid into your shares account ****0001 on 14/11/2026 10:00. New balance: GHS 75.00. Ref: 3F2A9C1B");
    }

    // Between two members: each gets their own message, without the other's name.
    @Test
    void transferBetweenMembersTextsBothSides() {
        MemberAccount from = account(member("0241234567"), "1000000002", AccountType.SAVINGS, "470");
        MemberAccount to = account(member("0207654321"), "1000000005", AccountType.CURRENT, "1030");

        smsService.transfer(transaction("30"), from, to);

        List<TransactionSmsEvent> events = sent(2);
        assertThat(events.get(0).getPhoneNumber()).isEqualTo("0241234567");
        assertThat(events.get(0).getMessage()).isEqualTo(
                "GHS 30.00 transferred from your SAVINGS account ****0002 to account ****0005 on 14/11/2026 10:00. New balance: GHS 470.00. Ref: 3F2A9C1B");
        assertThat(events.get(1).getPhoneNumber()).isEqualTo("0207654321");
        assertThat(events.get(1).getMessage()).isEqualTo(
                "GHS 30.00 received in your CURRENT account ****0005 from account ****0002 on 14/11/2026 10:00. New balance: GHS 1,030.00. Ref: 3F2A9C1B");
    }

    @Test
    void transferBetweenOwnAccountsIsOneMessage() {
        Member member = member("0241234567");

        smsService.transfer(transaction("30"), account(member, "1000000002", AccountType.SAVINGS, "470"),
                account(member, "1000000003", AccountType.CURRENT, "30"));

        assertThat(sent(1).get(0).getMessage()).isEqualTo(
                "GHS 30.00 moved from your SAVINGS account ****0002 to your CURRENT account ****0003 on 14/11/2026 10:00. "
                        + "Balances: SAVINGS GHS 470.00, CURRENT GHS 30.00. Ref: 3F2A9C1B");
    }

    @Test
    void memberWithoutAPhoneGetsNoMessage() {
        smsService.deposit(transaction("40"), account(member(" "), "1000000002", AccountType.SAVINGS, "140"));

        verifyNoInteractions(outboxService);
    }
}
