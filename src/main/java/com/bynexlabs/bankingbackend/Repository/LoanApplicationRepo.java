package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.ApplicationStatus;
import com.bynexlabs.bankingbackend.models.LoanApplication;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanApplicationRepo extends JpaRepository<LoanApplication, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanApplication a where a.id = :id")
    Optional<LoanApplication> lockById(@Param("id") UUID id);

    boolean existsByMember_IdAndStatus(UUID memberId, ApplicationStatus status);

    @EntityGraph(attributePaths = {"member", "member.branch", "loanProduct"})
    Page<LoanApplication> findByStatus(ApplicationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"member", "member.branch", "loanProduct"})
    Page<LoanApplication> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"member", "member.branch", "loanProduct"})
    Page<LoanApplication> findByMember_Id(UUID memberId, Pageable pageable);
}
