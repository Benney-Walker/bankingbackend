package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.MemberAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MemberAccountRepo extends JpaRepository<MemberAccount, UUID> {
    List<MemberAccount> findByMember_Id(UUID memberId);
}
