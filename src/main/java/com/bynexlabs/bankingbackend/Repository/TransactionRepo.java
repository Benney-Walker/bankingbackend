package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepo extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    /** Whether the member has made their first deposit (into any of their accounts). */
    boolean existsByMemberAccount_Member_IdAndTransactionTypeAndStatus(UUID memberId, TransactionType type,
                                                                      TransactionStatus status);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.memberAccount.id = :accountId and t.transactionType = :type and t.status = :status
              and t.createdAt >= :from and t.createdAt < :to
            """)
    BigDecimal sumAmounts(@Param("accountId") UUID accountId, @Param("type") TransactionType type,
                          @Param("status") TransactionStatus status,
                          @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** Transactions where the account is either side: the main account or the counterparty. */
    @Query(value = """
            select t from Transaction t left join t.counterpartyAccount c
            where t.memberAccount.id = :accountId or c.id = :accountId
            """, countQuery = """
            select count(t) from Transaction t left join t.counterpartyAccount c
            where t.memberAccount.id = :accountId or c.id = :accountId
            """)
    Page<Transaction> findForAccount(@Param("accountId") UUID accountId, Pageable pageable);
}
