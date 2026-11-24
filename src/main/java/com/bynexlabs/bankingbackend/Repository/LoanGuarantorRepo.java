package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.LoanGuarantor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanGuarantorRepo extends JpaRepository<LoanGuarantor, UUID> {

    @EntityGraph(attributePaths = {"member"})
    List<LoanGuarantor> findByLoanApplication_Id(UUID applicationId);
}
