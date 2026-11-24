package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.LoanAccountStatus;
import com.bynexlabs.bankingbackend.models.LoanAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanAccountRepo extends JpaRepository<LoanAccount, UUID> {

    boolean existsByMember_IdAndStatus(UUID memberId, LoanAccountStatus status);

    @EntityGraph(attributePaths = {"memberAccount", "loanApplication", "loanApplication.loanProduct"})
    List<LoanAccount> findByMember_IdOrderByCreatedAtDesc(UUID memberId);

    @EntityGraph(attributePaths = {"memberAccount", "member", "loanApplication", "loanApplication.loanProduct"})
    Optional<LoanAccount> findWithDetailsById(UUID id);

    Optional<LoanAccount> findByLoanApplication_Id(UUID applicationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoanAccount l where l.id = :id")
    Optional<LoanAccount> lockById(@Param("id") UUID id);

    /** Active loans with an installment due on or before the given day that isn't fully paid. */
    @Query("""
            select distinct s.loanAccount.id from AmortizationSchedule s
            where s.loanAccount.status = com.bynexlabs.bankingbackend.enums.LoanAccountStatus.ACTIVE
              and s.status <> com.bynexlabs.bankingbackend.enums.ScheduleStatus.PAID
              and s.dueDate <= :today
            """)
    List<UUID> findIdsWithInstallmentsDue(@Param("today") LocalDate today);
}
