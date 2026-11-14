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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns a transaction into journal lines using the posting rules stored in the database (V34),
 * so the GL accounts behind each kind of transaction can change without a code change.
 *
 * Runs inside the money-movement transaction (MANDATORY): the balance change, the transaction
 * row and its journal lines commit together or not at all. If the rules can't produce a
 * complete, balanced journal, nothing is saved and the request fails, rather than recording
 * money the ledger can't account for.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostingService {

    private final PostingRuleRepo postingRuleRepo;

    private final JournalEntryRepo journalEntryRepo;

    /**
     * What to post: the event, the account types the rules can match on, and the amount of each
     * part. Parts that are zero or absent produce no lines.
     */
    public record Posting(PostingEvent event, AccountType primaryType, AccountType counterpartyType,
                          Map<AmountPart, BigDecimal> amounts) {

        public static Posting of(PostingEvent event, AccountType primaryType, AccountType counterpartyType) {
            return new Posting(event, primaryType, counterpartyType, new EnumMap<>(AmountPart.class));
        }

        public Posting with(AmountPart part, BigDecimal amount) {
            amounts.put(part, amount);
            return this;
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<JournalEntry> post(Transaction transaction, Posting posting, LocalDateTime postedAt) {
        List<PostingRule> rules = postingRuleRepo.findByEventWithAccount(posting.event());
        if (rules.isEmpty()) {
            throw misconfigured(posting, "no posting rules are set up");
        }

        List<JournalEntry> lines = new ArrayList<>();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        Set<AmountPart> used = EnumSet.noneOf(AmountPart.class);

        for (PostingRule rule : rules) {
            if (!matches(rule, posting)) {
                continue;
            }
            BigDecimal amount = posting.amounts().getOrDefault(rule.getAmountPart(), BigDecimal.ZERO);
            if (amount.signum() == 0) {
                continue;
            }
            if (amount.signum() < 0) {
                throw new IllegalArgumentException("Posting amounts can't be negative: " + rule.getAmountPart());
            }
            GLAccount account = rule.getGlAccount();
            if (account.isHeader()) {
                throw misconfigured(posting, "rule " + rule.getId() + " posts to header account " + account.getAccountCode());
            }

            lines.add(JournalEntry.builder()
                    .transaction(transaction)
                    .glAccount(account)
                    .amount(amount)
                    .entryType(rule.getEntryType())
                    .createdAt(postedAt)
                    .build());
            used.add(rule.getAmountPart());
            if (rule.getEntryType() == EntryType.DEBIT) {
                debits = debits.add(amount);
            } else {
                credits = credits.add(amount);
            }
        }

        // Every amount that moved must reach the ledger, and the journal must balance. Together
        // these catch a missing rule: a fee taken from the member with no income rule leaves the
        // journal unbalanced, and a fee with no rules at all is an unused part.
        for (Map.Entry<AmountPart, BigDecimal> part : posting.amounts().entrySet()) {
            if (part.getValue().signum() != 0 && !used.contains(part.getKey())) {
                throw misconfigured(posting, "no matching rule for " + part.getKey());
            }
        }
        if (lines.size() < 2 || debits.compareTo(credits) != 0) {
            throw misconfigured(posting, "debits " + debits + " don't equal credits " + credits);
        }

        return journalEntryRepo.saveAll(lines);
    }

    static boolean matches(PostingRule rule, Posting posting) {
        if (rule.getMatchSide() == null) {
            return true;
        }
        AccountType type = rule.getMatchSide() == MatchSide.PRIMARY ? posting.primaryType() : posting.counterpartyType();
        return rule.getMatchAccountType() == type;
    }

    private static InternalServerError misconfigured(Posting posting, String problem) {
        // A configuration problem, not the user's fault: log the detail, keep the response generic.
        log.error("Posting rules for {} (primary {}, counterparty {}) are incomplete: {}",
                posting.event(), posting.primaryType(), posting.counterpartyType(), problem);
        return new InternalServerError("The ledger isn't set up for this transaction. Nothing was recorded.");
    }
}
