package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;
import com.bynexlabs.bankingbackend.models.CollectorSubmission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectorSubmissionRepo extends JpaRepository<CollectorSubmission, UUID> {

    Optional<CollectorSubmission> findByCollector_IdAndStatus(UUID collectorId, CollectorSubmissionStatus status);

    boolean existsByCollector_IdAndStatus(UUID collectorId, CollectorSubmissionStatus status);

    /** An approved submission made on the given day: the collector is closed until the next one. */
    boolean existsByCollector_IdAndStatusAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
            UUID collectorId, CollectorSubmissionStatus status, LocalDateTime from, LocalDateTime to);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from CollectorSubmission s where s.id = :id")
    Optional<CollectorSubmission> lockById(@Param("id") UUID id);

    Page<CollectorSubmission> findByCollector_Id(UUID collectorId, Pageable pageable);

    Page<CollectorSubmission> findByStatus(CollectorSubmissionStatus status, Pageable pageable);

    Page<CollectorSubmission> findByStatusAndBranch_Id(CollectorSubmissionStatus status, UUID branchId, Pageable pageable);
}
