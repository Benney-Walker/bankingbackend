package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Branch;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface BranchRepo extends JpaRepository<Branch, UUID> {

    // Staff changes that affect a branch's agent (add, role change, reactivate) lock the branch
    // row first, so two of them can't both see "no active agent" and both go ahead.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id = :id")
    Optional<Branch> lockById(@Param("id") UUID id);
}
