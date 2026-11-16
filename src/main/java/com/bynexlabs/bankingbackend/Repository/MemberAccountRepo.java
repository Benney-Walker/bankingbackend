package com.bynexlabs.bankingbackend.Repository;

import java.util.Collection;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MemberAccountRepo extends JpaRepository<MemberAccount, UUID> {

    List<MemberAccount> findByMember_Id(UUID memberId);

    @Query("select a.id from MemberAccount a where a.accountNumber = :accountNumber")
    Optional<UUID> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    @Query("select a.member.id from MemberAccount a where a.id = :id")
    Optional<UUID> findMemberIdById(@Param("id") UUID id);

    @Query("select a.id from MemberAccount a where a.member.id = :memberId and a.accountType = :type")
    List<UUID> findIdsByMemberIdAndType(@Param("memberId") UUID memberId, @Param("type") AccountType type);

    /** Accounts collectors can record against: ACTIVE ones of ACTIVE members in a branch, by member name. */
    @Query("""
            select a from MemberAccount a join fetch a.member m
            where m.branch.id = :branchId and m.membershipStatus = :memberStatus and a.status = :accountStatus
              and a.accountType in :types
            order by m.lastName, m.firstName, a.accountType
            """)
    List<MemberAccount> findForBranch(@Param("branchId") UUID branchId,
                                      @Param("memberStatus") MembershipStatus memberStatus,
                                      @Param("accountStatus") AccountStatus accountStatus,
                                      @Param("types") Collection<AccountType> types);

    @Query("select a from MemberAccount a join fetch a.member where a.accountNumber = :accountNumber")
    Optional<MemberAccount> findWithMemberByAccountNumber(@Param("accountNumber") String accountNumber);

    // SELECT ... FOR UPDATE (owner decision, 2026-11-07): one money movement per account at a
    // time. A second transaction on the same account waits here until the first commits, then
    // reads the updated balance. Only call this through AccountLockService.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from MemberAccount a join fetch a.member where a.id = :id")
    Optional<MemberAccount> lockById(@Param("id") UUID id);
}
