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
import java.time.LocalDateTime;
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

    /** Registers a collector's phone if none is registered yet. Returns 1 when it did. */
    @Modifying
    @Query("update Staff s set s.registeredDeviceId = :deviceId, s.deviceRegisteredAt = :at where s.id = :id and s.registeredDeviceId is null")
    int registerDevice(@Param("id") UUID id, @Param("deviceId") String deviceId, @Param("at") LocalDateTime at);

    /**
     * Counts a wrong password and locks the account when it reaches maxAttempts (V47). Does
     * nothing to an account that's already locked.
     */
    @Modifying
    @Query("""
            update Staff s set s.failedLoginAttempts = s.failedLoginAttempts + 1,
                s.lockedAt = case when s.failedLoginAttempts + 1 >= :maxAttempts then :now else s.lockedAt end
            where s.id = :id and s.lockedAt is null
            """)
    int recordFailedLogin(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts, @Param("now") LocalDateTime now);

    @Query("select s.failedLoginAttempts from Staff s where s.id = :id")
    int findFailedLoginAttempts(@Param("id") UUID id);

    /** A correct password starts the count again. */
    @Modifying
    @Query("update Staff s set s.failedLoginAttempts = 0 where s.id = :id and s.failedLoginAttempts > 0 and s.lockedAt is null")
    int clearFailedLogins(@Param("id") UUID id);

    /** Unlocks the account. Returns 1 when it was locked. */
    @Modifying
    @Query("update Staff s set s.failedLoginAttempts = 0, s.lockedAt = null where s.id = :id and s.lockedAt is not null")
    int unlock(@Param("id") UUID id);

    /** Clears a collector's registered phone. Returns 1 when one was registered. */
    @Modifying
    @Query("update Staff s set s.registeredDeviceId = null, s.deviceRegisteredAt = null where s.id = :id and s.registeredDeviceId is not null")
    int releaseDevice(@Param("id") UUID id);

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
