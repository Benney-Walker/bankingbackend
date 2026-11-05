package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.GLAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GLAccountRepo extends JpaRepository<GLAccount, UUID> {
}
