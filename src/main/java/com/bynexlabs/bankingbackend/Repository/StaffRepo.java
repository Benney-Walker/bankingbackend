package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StaffRepo extends JpaRepository<Staff, UUID> {
    Optional<Staff> findByStaffId(String staffId);

    // Status only, so the per-request check in JwtAuthenticationFilter doesn't load the whole
    // staff row (or its lazy roles) outside a transaction.
    @Query("select s.staffStatus from Staff s where s.staffId = :staffId")
    Optional<StaffStatus> findStatusByStaffId(@Param("staffId") String staffId);

    boolean existsByFirstNameAndLastNameAndDateOfBirth(
            String firstName, String lastName, LocalDate dateOfBirth
    );

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);
}
