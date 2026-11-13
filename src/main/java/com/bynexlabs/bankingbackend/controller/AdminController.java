package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.KycRecordResponse;
import com.bynexlabs.bankingbackend.DTO.member.KycRejectionRequest;
import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.service.KycService;
import com.bynexlabs.bankingbackend.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final MemberService memberService;

    private final AuthenticatedStaffProvider staffProvider;

    private final KycService kycService;

    @PutMapping("/v1/update-member/{memberId}")
    public MemberProfileResponse updateMember(@PathVariable UUID memberId,
                                              @Valid @RequestBody MemberUpdateRequest request) {
        return memberService.updateMemberContact(memberId, request);
    }

    @PostMapping("/v1/manual-kyc-approval/{memberId}")
    public ResponseEntity<?> approveKycInfo(@PathVariable UUID memberId) {

        Staff staff = staffProvider.getStaff();

        return kycService.approveKycInfoManually(memberId, staff);
    }

    @PostMapping("/v1/manual-kyc-rejection/{memberId}")
    public ResponseEntity<?> rejectKycInfo(@PathVariable UUID memberId,
                                           @Valid @RequestBody KycRejectionRequest request) {
        return kycService.rejectKycManually(memberId, request.getReason(), staffProvider.getStaff());
    }

    @GetMapping("/v1/kyc-reviews")
    public PagedModel<KycRecordResponse> listKycReviews(@RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return kycService.listForReview(status, page, size, staffProvider.getStaffId(),
                staffProvider.hasRole(Roles.ADMINISTRATOR));
    }

    @GetMapping("/v1/kyc-details/{memberId}")
    public KycRecordResponse getKycRecord(@PathVariable UUID memberId) {

        return kycService.getKycRecord(memberId, staffProvider.getStaffId(), staffProvider.hasRole(Roles.ADMINISTRATOR));
    }
}
