package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ledger.TrialBalanceResponse;
import com.bynexlabs.bankingbackend.Repository.JournalEntryRepo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private JournalEntryRepo journalEntryRepo;

    @InjectMocks
    private LedgerService ledgerService;

    private static JournalEntryRepo.AccountTotals totals(String code, String category, String debits, String credits) {
        return new JournalEntryRepo.AccountTotals() {
            public String getAccountCode() { return code; }
            public String getName() { return code; }
            public String getCategory() { return category; }
            public BigDecimal getTotalDebits() { return new BigDecimal(debits); }
            public BigDecimal getTotalCredits() { return new BigDecimal(credits); }
        };
    }

    // Deposit 120 (50 to shares), withdraw 30 with a 2 fee.
    @Test
    void balancesAreShownOnEachAccountsNormalSide() {
        when(journalEntryRepo.totalsByAccount()).thenReturn(List.of(
                totals("1010", "ASSET", "120", "30"),
                totals("2010", "LIABILITY", "32", "70"),
                totals("3010", "EQUITY", "0", "50"),
                totals("4020", "REVENUE", "0", "2")));

        TrialBalanceResponse trialBalance = ledgerService.trialBalance();

        assertThat(trialBalance.accounts()).extracting(line -> line.accountCode() + " " + line.balance().toPlainString())
                .containsExactly("1010 90", "2010 38", "3010 50", "4020 2");
        assertThat(trialBalance.totalDebits()).isEqualByComparingTo("152");
        assertThat(trialBalance.totalCredits()).isEqualByComparingTo("152");
        assertThat(trialBalance.balanced()).isTrue();
    }

    @Test
    void unequalTotalsAreFlagged() {
        when(journalEntryRepo.totalsByAccount()).thenReturn(List.of(totals("1010", "ASSET", "10", "0")));

        assertThat(ledgerService.trialBalance().balanced()).isFalse();
    }
}
