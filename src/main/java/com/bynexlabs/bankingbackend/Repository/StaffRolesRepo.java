package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRolesRepo extends JpaRepository<StaffRoles, Long> {
}
