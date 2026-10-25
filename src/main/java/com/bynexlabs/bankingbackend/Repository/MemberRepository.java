package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberRepository extends JpaRepository<Member, Long> {
}
