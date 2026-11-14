package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.JournalEntryRepo;
import com.bynexlabs.bankingbackend.Repository.PostingRuleRepo;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.EntryType;
import com.bynexlabs.bankingbackend.enums.MatchSide;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.exception.InternalServerError;
import com.bynexlabs.bankingbackend.models.GLAccount;
import com.bynexlabs.bankingbackend.models.JournalEntry;
import com.bynexlabs.bankingbackend.models.PostingRule;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.service.PostingService.Posting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 14, 10, 0);

    @Mock
    private PostingRuleRepo postingRuleRepo;

    @Mock
    private JournalEntryRepo journalEntryRepo;

    @InjectMocks
    private PostingService postingService;

    private final GLAccount cash = gl("1010", false);
    private final GLAccount savings = gl("2010", false);
    private final GLAccount current = gl("2015", false);
    private final GLAccount shareCapital = gl("3010", false);
    private final GLAccount feeIncome = gl("4020", false);

    private static GLAccount gl(String code, boolean header) {
        return GLAccount.builder().accountCode(code).name(code).header(header).build();
    }

    private static PostingRule rule(PostingEvent event, AmountPart part, EntryType type, GLAccount account,
                                    MatchSide side, AccountType accountType) {
        return PostingRule.builder().event(event).amountPart(part).entryType(type).glAccount(account)
                .matchSide(side).matchAccountType(accountType).build();
    }

    /** The V34 withdrawal rules. */
    private List<PostingRule> withdrawalRules() {
        return new ArrayList<>(List.of(
                rule(PostingEvent.WITHDRAWAL, AmountPart.PRINCIPAL, EntryType.DEBIT, savings, MatchSide.PRIMARY, AccountType.SAVINGS),
                rule(PostingEvent.WITHDRAWAL, AmountPart.PRINCIPAL, EntryType.DEBIT, current, MatchSide.PRIMARY, AccountType.CURRENT),
                rule(PostingEvent.WITHDRAWAL, AmountPart.PRINCIPAL, EntryType.CREDIT, cash, null, null),
                rule(PostingEvent.WITHDRAWAL, AmountPart.FEE, EntryType.DEBIT, savings, MatchSide.PRIMARY, AccountType.SAVINGS),
                rule(PostingEvent.WITHDRAWAL, AmountPart.FEE, EntryType.DEBIT, current, MatchSide.PRIMARY, AccountType.CURRENT),
                rule(PostingEvent.WITHDRAWAL, AmountPart.FEE, EntryType.CREDIT, feeIncome, null, null)));
    }

    @SuppressWarnings("unchecked")
    private List<JournalEntry> savedLines() {
        ArgumentCaptor<List<JournalEntry>> lines = ArgumentCaptor.forClass(List.class);
        verify(journalEntryRepo).saveAll(lines.capture());
        return lines.getValue();
    }

    private static String describe(JournalEntry line) {
        return line.getEntryType().name().charAt(0) + line.getGlAccount().getAccountCode() + " " + line.getAmount().toPlainString();
    }

    @Test
    void postsOnlyTheRulesForTheAccountTypeAndSkipsZeroAmounts() {
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.WITHDRAWAL)).thenReturn(withdrawalRules());
        Transaction transaction = new Transaction();

        postingService.post(transaction, Posting.of(PostingEvent.WITHDRAWAL, AccountType.CURRENT, null)
                .with(AmountPart.PRINCIPAL, new BigDecimal("100"))
                .with(AmountPart.FEE, BigDecimal.ZERO), NOW);

        List<JournalEntry> lines = savedLines();
        assertThat(lines).extracting(PostingServiceTest::describe).containsExactly("D2015 100", "C1010 100");
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.getTransaction()).isSameAs(transaction);
            assertThat(line.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void withdrawalWithFeeBalances() {
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.WITHDRAWAL)).thenReturn(withdrawalRules());

        postingService.post(new Transaction(), Posting.of(PostingEvent.WITHDRAWAL, AccountType.SAVINGS, null)
                .with(AmountPart.PRINCIPAL, new BigDecimal("100"))
                .with(AmountPart.FEE, new BigDecimal("2")), NOW);

        assertThat(savedLines()).extracting(PostingServiceTest::describe)
                .containsExactly("D2010 100", "C1010 100", "D2010 2", "C4020 2");
    }

    // First deposit: cash in for the whole amount, split between share capital and the member.
    @Test
    void splitEventBalancesAcrossParts() {
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.FIRST_DEPOSIT)).thenReturn(List.of(
                rule(PostingEvent.FIRST_DEPOSIT, AmountPart.PRINCIPAL, EntryType.DEBIT, cash, null, null),
                rule(PostingEvent.FIRST_DEPOSIT, AmountPart.SHARE, EntryType.CREDIT, shareCapital, null, null),
                rule(PostingEvent.FIRST_DEPOSIT, AmountPart.REMAINDER, EntryType.CREDIT, savings, MatchSide.PRIMARY, AccountType.SAVINGS)));

        postingService.post(new Transaction(), Posting.of(PostingEvent.FIRST_DEPOSIT, AccountType.SAVINGS, AccountType.SHARES)
                .with(AmountPart.PRINCIPAL, new BigDecimal("120"))
                .with(AmountPart.SHARE, new BigDecimal("50"))
                .with(AmountPart.REMAINDER, new BigDecimal("70")), NOW);

        assertThat(savedLines()).extracting(PostingServiceTest::describe)
                .containsExactly("D1010 120", "C3010 50", "C2010 70");
    }

    @Test
    void counterpartySideMatchesTheOtherAccountsType() {
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.TRANSFER)).thenReturn(List.of(
                rule(PostingEvent.TRANSFER, AmountPart.PRINCIPAL, EntryType.DEBIT, savings, MatchSide.PRIMARY, AccountType.SAVINGS),
                rule(PostingEvent.TRANSFER, AmountPart.PRINCIPAL, EntryType.DEBIT, current, MatchSide.PRIMARY, AccountType.CURRENT),
                rule(PostingEvent.TRANSFER, AmountPart.PRINCIPAL, EntryType.CREDIT, savings, MatchSide.COUNTERPARTY, AccountType.SAVINGS),
                rule(PostingEvent.TRANSFER, AmountPart.PRINCIPAL, EntryType.CREDIT, current, MatchSide.COUNTERPARTY, AccountType.CURRENT)));

        postingService.post(new Transaction(), Posting.of(PostingEvent.TRANSFER, AccountType.SAVINGS, AccountType.CURRENT)
                .with(AmountPart.PRINCIPAL, new BigDecimal("30")), NOW);

        assertThat(savedLines()).extracting(PostingServiceTest::describe).containsExactly("D2010 30", "C2015 30");
    }

    // A fee taken from the member with no income rule would leave the journal unbalanced.
    @Test
    void missingRuleThatUnbalancesTheJournalIsRefused() {
        List<PostingRule> rules = withdrawalRules();
        rules.removeIf(rule -> rule.getGlAccount() == feeIncome);
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.WITHDRAWAL)).thenReturn(rules);

        assertThatThrownBy(() -> postingService.post(new Transaction(), Posting.of(PostingEvent.WITHDRAWAL, AccountType.SAVINGS, null)
                .with(AmountPart.PRINCIPAL, new BigDecimal("100"))
                .with(AmountPart.FEE, new BigDecimal("2")), NOW))
                .isInstanceOf(InternalServerError.class)
                .hasMessage("The ledger isn't set up for this transaction. Nothing was recorded.");
        verify(journalEntryRepo, never()).saveAll(anyList());
    }

    @Test
    void amountWithNoRulesAtAllIsRefused() {
        List<PostingRule> rules = withdrawalRules();
        rules.removeIf(rule -> rule.getAmountPart() == AmountPart.FEE);
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.WITHDRAWAL)).thenReturn(rules);

        assertThatThrownBy(() -> postingService.post(new Transaction(), Posting.of(PostingEvent.WITHDRAWAL, AccountType.SAVINGS, null)
                .with(AmountPart.PRINCIPAL, new BigDecimal("100"))
                .with(AmountPart.FEE, new BigDecimal("2")), NOW))
                .isInstanceOf(InternalServerError.class);
        verify(journalEntryRepo, never()).saveAll(anyList());
    }

    @Test
    void eventWithoutRulesIsRefused() {
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.DEPOSIT)).thenReturn(List.of());

        assertThatThrownBy(() -> postingService.post(new Transaction(), Posting.of(PostingEvent.DEPOSIT, AccountType.SAVINGS, null)
                .with(AmountPart.PRINCIPAL, BigDecimal.TEN), NOW))
                .isInstanceOf(InternalServerError.class);
    }

    @Test
    void ruleOnAHeaderAccountIsRefused() {
        when(postingRuleRepo.findByEventWithAccount(PostingEvent.DEPOSIT)).thenReturn(List.of(
                rule(PostingEvent.DEPOSIT, AmountPart.PRINCIPAL, EntryType.DEBIT, gl("1000", true), null, null),
                rule(PostingEvent.DEPOSIT, AmountPart.PRINCIPAL, EntryType.CREDIT, savings, null, null)));

        assertThatThrownBy(() -> postingService.post(new Transaction(), Posting.of(PostingEvent.DEPOSIT, AccountType.SAVINGS, null)
                .with(AmountPart.PRINCIPAL, BigDecimal.TEN), NOW))
                .isInstanceOf(InternalServerError.class);
        verify(journalEntryRepo, never()).saveAll(anyList());
    }
}
