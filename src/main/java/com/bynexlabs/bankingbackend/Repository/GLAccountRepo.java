package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.GLAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GLAccountRepo extends JpaRepository<GLAccount, Long> {
}
