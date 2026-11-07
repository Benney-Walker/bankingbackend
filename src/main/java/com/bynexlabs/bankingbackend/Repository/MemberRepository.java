package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberRepository extends JpaRepository<Member, UUID> {
    Optional<Member> findByPhoneNumber(String phoneNumber);
    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID id);

    // Free-text member search: part of a first, middle, last or full name (case-insensitive),
    // part of a phone number, or an exact account number.
    @Query("""
            select m from Member m
            where lower(m.firstName) like lower(concat('%', :query, '%'))
               or lower(m.middleName) like lower(concat('%', :query, '%'))
               or lower(m.lastName) like lower(concat('%', :query, '%'))
               or lower(concat(m.firstName, ' ', m.lastName)) like lower(concat('%', :query, '%'))
               or m.phoneNumber like concat('%', :query, '%')
               or exists (select 1 from MemberAccount a where a.member = m and a.accountNumber = :query)
            """)
    Page<Member> search(@Param("query") String query, Pageable pageable);
}
