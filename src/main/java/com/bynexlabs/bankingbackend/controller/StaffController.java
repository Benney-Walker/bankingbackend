package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.DTO.staff.StaffUpdateRequest;
import com.bynexlabs.bankingbackend.service.MemberService;
import com.bynexlabs.bankingbackend.service.StaffService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @GetMapping("/v1/all-staff")
    public PagedModel<StaffResponse> listStaff(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return staffService.listStaff(page, size);
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @GetMapping("/v1/staff-details/{staffId}")
    public StaffResponse getStaff(@PathVariable String staffId) {
        return staffService.getStaff(staffId);
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @PutMapping("/v1/update-staff/{staffId}")
    public StaffResponse updateStaff(@PathVariable String staffId,
                                     @Valid @RequestBody StaffUpdateRequest request) {
        return staffService.updateStaffProfile(staffId, request);
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @PostMapping("/v1/add-staff")
    public ResponseEntity<?> addNewStaff(@Valid @RequestBody StaffDetails staffDetails) {
        return staffService.addNewStaff(staffDetails);
    }
}
