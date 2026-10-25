package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.MemberAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberAccountRepo extends JpaRepository<MemberAccount, Long> {
}
