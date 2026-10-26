package com.bynexlabs.bankingbackend.Utility;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AuthenticatedStaffProvider {

    private final StaffRepo staffRepo;

    public String getStaffId() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return authentication.getName();
    }

    public Staff getStaff() {
        String staffId = getStaffId();
        if (staffId == null) {
            return null;
        }
        Staff staff = staffRepo.findByStaffId(staffId).orElse(null);
        if (staff == null || staff.getStaffStatus() == StaffStatus.INACTIVE) {
            return null;
        }
        return staff;
    }
}
