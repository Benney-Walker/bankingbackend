package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanApplicationRepo extends JpaRepository<LoanApplication, Long> {
}
