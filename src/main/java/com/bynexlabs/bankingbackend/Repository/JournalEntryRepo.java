package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface JournalEntryRepo extends JpaRepository<JournalEntry, UUID> {

    /** Debit and credit totals per postable GL account, including accounts with no lines yet. */
    interface AccountTotals {
        String getAccountCode();

        String getName();

        String getCategory();

        BigDecimal getTotalDebits();

        BigDecimal getTotalCredits();
    }

    @Query(nativeQuery = true, value = """
            SELECT g.account_code AS accountCode, g.name AS name, g.category AS category,
                   COALESCE(SUM(CASE WHEN j.entry_type = 'DEBIT' THEN j.amount END), 0) AS totalDebits,
                   COALESCE(SUM(CASE WHEN j.entry_type = 'CREDIT' THEN j.amount END), 0) AS totalCredits
            FROM gl_account g
            LEFT JOIN journal_entry j ON j.gl_account_id = g.id
            WHERE g.is_header = FALSE
            GROUP BY g.id, g.account_code, g.name, g.category
            ORDER BY g.account_code
            """)
    List<AccountTotals> totalsByAccount();
}
