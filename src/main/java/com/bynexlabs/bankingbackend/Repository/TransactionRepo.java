package com.bynexlabs.bankingbackend.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
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

    // Corrections lock the row they change, so they can't overlap with each other or with the
    // finaliser applying the same payment.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Transaction t where t.id = :id")
    Optional<Transaction> lockById(@Param("id") UUID id);

    /** Collector payments whose correction window closed before the given time, oldest first. */
    @Query(value = """
            select id from transaction
            where status = 'PENDING' and channel = 'COLLECTOR' and created_at <= :closedBefore
            order by created_at
            limit :limit
            """, nativeQuery = true)
    List<UUID> findDuePendingCollectorPayments(@Param("closedBefore") LocalDateTime closedBefore,
                                               @Param("limit") int limit);

    // SKIP LOCKED: if a correction (or another app instance) holds the row, the finaliser leaves
    // it for its next run instead of waiting.
    @Query(value = """
            select * from transaction
            where id = :id and status = 'PENDING' and channel = 'COLLECTOR'
            for update skip locked
            """, nativeQuery = true)
    Optional<Transaction> lockPendingCollectorPayment(@Param("id") UUID id);

    List<Transaction> findByInitiatedBy_IdAndChannelAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            UUID staffId, TransactionChannel channel, LocalDateTime from, LocalDateTime to);

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
