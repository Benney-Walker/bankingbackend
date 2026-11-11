package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BranchRepo extends JpaRepository<Branch, UUID> {
}
