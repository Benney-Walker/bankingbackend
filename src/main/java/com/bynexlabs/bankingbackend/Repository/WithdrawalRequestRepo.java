package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.WithdrawalRequestStatus;
import com.bynexlabs.bankingbackend.models.WithdrawalRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WithdrawalRequestRepo extends JpaRepository<WithdrawalRequest, UUID> {

    Optional<WithdrawalRequest> findByIdempotencyKey(String idempotencyKey);

    // Corrections, reviews and payment change a request one at a time.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from WithdrawalRequest r where r.id = :id")
    Optional<WithdrawalRequest> lockById(@Param("id") UUID id);

    Page<WithdrawalRequest> findByStatus(WithdrawalRequestStatus status, Pageable pageable);

    Page<WithdrawalRequest> findByRequestedBy_Id(UUID staffId, Pageable pageable);
}
