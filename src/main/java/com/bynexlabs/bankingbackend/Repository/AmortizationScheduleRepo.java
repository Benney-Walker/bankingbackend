package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AmortizationScheduleRepo extends JpaRepository<AmortizationSchedule, UUID> {

    List<AmortizationSchedule> findByLoanAccount_IdOrderByInstallmentNumberAsc(UUID loanAccountId);

    /** Installments due on or before the given day and not fully paid, oldest first. */
    @Query("""
            select s from AmortizationSchedule s
            where s.loanAccount.id = :loanId
              and s.status <> com.bynexlabs.bankingbackend.enums.ScheduleStatus.PAID
              and s.dueDate <= :today
            order by s.installmentNumber
            """)
    List<AmortizationSchedule> findDueUnpaid(@Param("loanId") UUID loanId, @Param("today") LocalDate today);
}
