package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.service.MemberService;
import com.bynexlabs.bankingbackend.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    @PostMapping("/v1/add-staff")
    public ResponseEntity<?> addNewStaff(@Valid @RequestBody StaffDetails staffDetails) {
        return staffService.addNewStaff(staffDetails);
    }
}
