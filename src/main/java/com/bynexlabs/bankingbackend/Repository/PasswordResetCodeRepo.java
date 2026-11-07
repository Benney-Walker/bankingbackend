package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.PasswordResetCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetCodeRepo extends JpaRepository<PasswordResetCode, UUID> {

    Optional<PasswordResetCode> findByStaff_Id(UUID staffId);

    // Row lock while a code is being checked, so parallel guesses can't each read the same
    // attempt count and slip past the wrong-attempt limit. Must run inside a transaction.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetCode c where c.staff.id = :staffId")
    Optional<PasswordResetCode> lockByStaffId(@Param("staffId") UUID staffId);
}
