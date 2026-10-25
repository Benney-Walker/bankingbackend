package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberKycRecordRepo extends JpaRepository<MemberKycRecord, Long> {
}
