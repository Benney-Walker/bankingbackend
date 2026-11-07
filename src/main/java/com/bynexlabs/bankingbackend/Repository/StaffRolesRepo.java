package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRolesRepo extends JpaRepository<StaffRoles, Long> {

    // How many staff, other than the given one, hold this role and have this status. Used to
    // make sure the credit union always keeps at least one active administrator.
    @Query("""
            select count(distinct r.staff.id) from StaffRoles r
            where r.role = :role and r.staff.staffStatus = :status and r.staff.staffId <> :staffId
            """)
    long countOtherStaffWithRoleAndStatus(@Param("role") Roles role,
                                          @Param("status") StaffStatus status,
                                          @Param("staffId") String staffId);
}
