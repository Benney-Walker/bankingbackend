package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.MemberKycRecord;
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

    boolean existsByMember_Id(UUID memberId);

    // Locks the record while an automated result is written, so it can't race a manual approval
    // or a duplicate delivery of the same verification message.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from MemberKycRecord r where r.id = :id")
    Optional<MemberKycRecord> lockById(@Param("id") UUID id);
}
