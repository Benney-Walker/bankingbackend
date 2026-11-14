package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ledger.TrialBalanceResponse;
import com.bynexlabs.bankingbackend.Repository.JournalEntryRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Read side of the general ledger. */
@Service
@RequiredArgsConstructor
public class LedgerService {

    // Assets and expenses grow with debits; liabilities, equity and revenue with credits.
    private static final Set<String> DEBIT_NORMAL = Set.of("ASSET", "EXPENSE");

    private final JournalEntryRepo journalEntryRepo;

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance() {
        List<TrialBalanceResponse.Line> lines = journalEntryRepo.totalsByAccount().stream()
                .map(totals -> new TrialBalanceResponse.Line(
                        totals.getAccountCode(),
                        totals.getName(),
                        totals.getCategory(),
                        totals.getTotalDebits(),
                        totals.getTotalCredits(),
                        DEBIT_NORMAL.contains(totals.getCategory())
                                ? totals.getTotalDebits().subtract(totals.getTotalCredits())
                                : totals.getTotalCredits().subtract(totals.getTotalDebits())))
                .toList();

        BigDecimal debits = lines.stream().map(TrialBalanceResponse.Line::totalDebits).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = lines.stream().map(TrialBalanceResponse.Line::totalCredits).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new TrialBalanceResponse(lines, debits, credits, debits.compareTo(credits) == 0);
    }
}
