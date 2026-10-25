package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.LoanAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanAccountRepo extends JpaRepository<LoanAccount, Long> {
}
