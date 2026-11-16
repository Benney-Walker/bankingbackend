package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Staff;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepo extends JpaRepository<Staff, UUID> {
    Optional<Staff> findByStaffId(String staffId);

    /** Ends every session at once: access tokens issued before this moment are refused. */
    @Modifying
    @Query("update Staff s set s.sessionsValidAfter = :cutoff where s.id = :id")
    int cutSessions(@Param("id") UUID id, @Param("cutoff") Instant cutoff);

    /** Serialises one collector's payments with their daily submission. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Staff s where s.id = :id")
    Optional<Staff> lockById(@Param("id") UUID id);

    List<Staff> findByBranch_Id(UUID branchId);

    boolean existsByFirstNameAndLastNameAndDateOfBirth(
            String firstName, String lastName, LocalDate dateOfBirth
    );

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    // Duplicate checks for updates: the same values on the staff member being edited are fine.
    boolean existsByFirstNameAndLastNameAndDateOfBirthAndStaffIdNot(
            String firstName, String lastName, LocalDate dateOfBirth, String staffId
    );

    boolean existsByPhoneNumberAndStaffIdNot(String phoneNumber, String staffId);

    boolean existsByEmailAndStaffIdNot(String email, String staffId);
}
