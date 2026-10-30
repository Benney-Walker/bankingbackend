package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberKycRecordRepo extends JpaRepository<MemberKycRecord, UUID> {
    Optional<MemberKycRecord> findByMember_Id(UUID memberId);
}
