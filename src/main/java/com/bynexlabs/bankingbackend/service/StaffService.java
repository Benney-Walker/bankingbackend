package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StaffService {

    private final StaffRepo staffRepo;

    public Staff getStaffDetails(String staffId){
        return staffRepo.findByStaffId(staffId).orElse(null);
    }
}
