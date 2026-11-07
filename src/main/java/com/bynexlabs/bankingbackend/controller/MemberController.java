package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.KycRejectionRequest;
import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberSummaryResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.service.KycService;
import com.bynexlabs.bankingbackend.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    private final KycService kycService;

    private final AuthenticatedStaffProvider staffProvider;

    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'AGENT', 'COLLECTOR')")
    @GetMapping("/v1/search-members")
    public PagedModel<MemberSummaryResponse> searchMembers(@RequestParam(required = false) String query,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return memberService.searchMembers(query, page, size);
    }

    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'AGENT', 'COLLECTOR')")
    @GetMapping("/v1/member-details/{memberId}")
    public MemberProfileResponse getMember(@PathVariable UUID memberId) {
        return memberService.getMemberProfile(memberId);
    }

    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'AGENT')")
    @PutMapping("/v1/update-member/{memberId}")
    public MemberProfileResponse updateMember(@PathVariable UUID memberId,
                                              @Valid @RequestBody MemberUpdateRequest request) {
        return memberService.updateMemberContact(memberId, request);
    }

    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'AGENT')")
    @PostMapping("/v1/add-member")
    public ResponseEntity<?> addNewMember(@Valid @RequestBody MemberDetails memberDetails) {
        return memberService.registerNewMember(memberDetails, staffProvider.getStaff());
    }

    @PreAuthorize("hasAnyAuthority('ADMINISTRATOR', 'AGENT')")
    @PostMapping(
            value = "v1/kyc-document-upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> submitKyc(@RequestParam("memberId") UUID memberId,
                                       @RequestParam("documentType") String documentType,
                                       @RequestParam("idNumber") String idNumber,
                                       @RequestParam("file") MultipartFile file) {

        return kycService.submitKyc(memberId, documentType, idNumber, file, staffProvider.getStaff());
    }

    // Owner rule (2026-11-07): only an ADMINISTRATOR approves or rejects KYC manually.
    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @PostMapping("/v1/manual-kyc-approval/{memberId}")
    public ResponseEntity<?> approveKycInfo(@PathVariable UUID memberId) {

        Staff staff = staffProvider.getStaff();

        return kycService.approveKycInfoManually(memberId, staff);
    }

    @PreAuthorize("hasAuthority('ADMINISTRATOR')")
    @PostMapping("/v1/manual-kyc-rejection/{memberId}")
    public ResponseEntity<?> rejectKycInfo(@PathVariable UUID memberId,
                                           @Valid @RequestBody KycRejectionRequest request) {
        return kycService.rejectKycManually(memberId, request.getReason(), staffProvider.getStaff());
    }
}
