package com.bynexlabs.bankingbackend.Repository;

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

    // Used by JwtAuthenticationFilter on every request. Roles are fetched in the same query
    // because the filter runs outside a transaction, where the lazy collection can't load.
    @Query("select distinct s from Staff s left join fetch s.roles where s.staffId = :staffId")
    Optional<Staff> findWithRolesByStaffId(@Param("staffId") String staffId);

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
