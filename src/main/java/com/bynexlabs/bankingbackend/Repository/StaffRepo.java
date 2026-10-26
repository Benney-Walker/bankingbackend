package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface StaffRepo extends JpaRepository<Staff, Long> {
    Optional<Staff> findByStaffId(String staffId);
}
