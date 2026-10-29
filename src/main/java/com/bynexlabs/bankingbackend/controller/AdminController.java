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
@RequestMapping("/api/admin")
public class AdminController {

    private final StaffService staffService;

    private final MemberService memberService;

    @PostMapping("/v1/add-staff")
    public ResponseEntity<?> addNewStaff(@Valid @RequestBody StaffDetails staffDetails) {
        return staffService.addNewStaff(staffDetails);
    }

    @PostMapping("/v1/add-member")
    public ResponseEntity<?> addNewMember(@Valid @RequestBody MemberDetails memberDetails) {
        return memberService.registerNewMember(memberDetails);
    }
}
