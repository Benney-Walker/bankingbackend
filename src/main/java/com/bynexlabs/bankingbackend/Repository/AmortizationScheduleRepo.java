package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmortizationScheduleRepo extends JpaRepository<AmortizationSchedule, Long> {
}
