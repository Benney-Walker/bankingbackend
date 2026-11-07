package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberKycRecordRepo extends JpaRepository<MemberKycRecord, UUID> {
    Optional<MemberKycRecord> findByMember_Id(UUID memberId);

    // Review list (roadmap 3.7): administrators see every record with the status...
    Page<MemberKycRecord> findByStatus(KycStatus status, Pageable pageable);

    // ...agents only those of members they registered (owner rule, 2026-11-07).
    Page<MemberKycRecord> findByStatusAndMember_RegisteredBy_StaffId(KycStatus status, String staffId, Pageable pageable);

    // Owner rule: an ID document number belongs to one member only.
    boolean existsByIdNumberAndMember_IdNot(String idNumber, UUID memberId);

    // Locks the record while an automated result is written, so it can't race a manual approval
    // or a duplicate delivery of the same verification message.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MemberKycRecord r where r.id = :id")
    Optional<MemberKycRecord> lockById(@Param("id") UUID id);
}
