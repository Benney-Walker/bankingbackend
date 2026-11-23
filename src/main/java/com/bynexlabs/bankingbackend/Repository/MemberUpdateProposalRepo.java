package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.MemberUpdateStatus;
import com.bynexlabs.bankingbackend.models.MemberUpdateProposal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberUpdateProposalRepo extends JpaRepository<MemberUpdateProposal, UUID> {

    boolean existsByMember_IdAndStatus(UUID memberId, MemberUpdateStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from MemberUpdateProposal p where p.id = :id")
    Optional<MemberUpdateProposal> lockById(@Param("id") UUID id);

    Page<MemberUpdateProposal> findByStatus(MemberUpdateStatus status, Pageable pageable);

    Page<MemberUpdateProposal> findByRequestedBy_Id(UUID staffId, Pageable pageable);
}
