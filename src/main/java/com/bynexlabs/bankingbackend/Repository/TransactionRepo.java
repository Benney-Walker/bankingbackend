package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransactionRepo extends JpaRepository<Transaction, Long> {
}
