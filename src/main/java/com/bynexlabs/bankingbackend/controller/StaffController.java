package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffDetails;
import com.bynexlabs.bankingbackend.DTO.staff.StaffResponse;
import com.bynexlabs.bankingbackend.DTO.staff.StaffRolesRequest;
import com.bynexlabs.bankingbackend.DTO.staff.StaffUpdateRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
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

    private final AuthenticatedStaffProvider staffProvider;

    @GetMapping("/v1/all-staff")
    public ResponseEntity<?> listStaff() {
        return ResponseEntity.ok(staffService.listStaff(staffProvider.getStaff()));
    }

    @GetMapping("/v1/staff-details/{staffId}")
    public StaffResponse getStaff(@PathVariable String staffId) {
        return staffService.getStaff(staffId);
    }

    @PutMapping("/v1/update-staff/{staffId}")
    public StaffResponse updateStaff(@PathVariable String staffId,
                                     @Valid @RequestBody StaffUpdateRequest request) {
        return staffService.updateStaffProfile(staffId, request);
    }

    @PutMapping("/v1/update-roles/{staffId}")
    public StaffResponse updateRoles(@PathVariable String staffId,
                                     @Valid @RequestBody StaffRolesRequest request) {
        return staffService.updateStaffRoles(staffId, request, staffProvider.getStaffId());
    }

    @PostMapping("/v1/deactivate/{staffId}")
    public StaffResponse deactivateStaff(@PathVariable String staffId) {
        return staffService.deactivateStaff(staffId, staffProvider.getStaffId());
    }

    @PostMapping("/v1/reactivate/{staffId}")
    public StaffResponse reactivateStaff(@PathVariable String staffId) {
        return staffService.reactivateStaff(staffId);
    }

    @PostMapping("/v1/add-staff")
    public ResponseEntity<?> addNewStaff(@Valid @RequestBody StaffDetails staffDetails) {
        return staffService.addNewStaff(staffDetails);
    }
}
