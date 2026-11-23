package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.KycRecordResponse;
import com.bynexlabs.bankingbackend.DTO.member.KycRejectionRequest;
import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberSummaryResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateProposalResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.service.KycService;
import com.bynexlabs.bankingbackend.service.MemberService;
import com.bynexlabs.bankingbackend.service.MemberUpdateProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
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

    private final MemberUpdateProposalService memberUpdateProposalService;

    /**
     * Search members of every branch by part of a name, part of a phone number, or an exact
     * account number. For another branch's member an agent doesn't see the phone number.
     */
    @GetMapping("/v1/search-members")
    public PagedModel<MemberSummaryResponse> searchMembers(@RequestParam(required = false) String query,
                                                           @RequestParam(defaultValue = "0") int page,
                                                           @RequestParam(defaultValue = "20") int size) {
        return memberService.searchMembers(query, page, size, staffProvider.getStaff(),
                staffProvider.hasRole(Roles.ADMINISTRATOR));
    }

    /**
     * Members, newest first. Administrators: every branch, or one with branchId. Agents: their
     * own branch only (another branchId is 403). Optional status filter.
     */
    @GetMapping("/v1/members")
    public PagedModel<MemberSummaryResponse> listMembers(@RequestParam(required = false) MembershipStatus status,
                                                         @RequestParam(required = false) UUID branchId,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
        return memberService.listMembers(status, branchId, page, size, staffProvider.getStaff(),
                staffProvider.hasRole(Roles.ADMINISTRATOR));
    }

    /**
     * Propose a new phone number, address and occupation for a member of any branch; an
     * administrator approves or rejects it. One waiting proposal per member (409).
     */
    @PostMapping("/v1/update-requests/{memberId}")
    public MemberUpdateProposalResponse proposeMemberUpdate(@PathVariable UUID memberId,
                                                            @Valid @RequestBody MemberUpdateRequest request) {
        return memberUpdateProposalService.propose(memberId, request, staffProvider.getStaff());
    }

    /** The signed-in staff member's own proposals and their outcome (rejection reason included). */
    @GetMapping("/v1/update-requests/mine")
    public PagedModel<MemberUpdateProposalResponse> myMemberUpdates(@RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "20") int size) {
        return memberUpdateProposalService.mine(staffProvider.getStaff(), page, size);
    }

    @GetMapping("/v1/member-details/{memberId}")
    public MemberProfileResponse getMember(@PathVariable UUID memberId) {
        return memberService.getMemberProfile(memberId);
    }

    @PostMapping("/v1/add-member")
    public ResponseEntity<?> addNewMember(@Valid @RequestBody MemberDetails memberDetails) {
        return memberService.registerNewMember(memberDetails, staffProvider.getStaff());
    }

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

    @GetMapping("/v1/kyc-document/{memberId}")
    public ResponseEntity<byte[]> getKycDocument(@PathVariable UUID memberId) {
        KycService.KycDocumentRef document = kycService.findDocumentForViewer(
                memberId, staffProvider.getStaffId(), staffProvider.hasRole(Roles.ADMINISTRATOR));
        byte[] content = kycService.downloadDocument(document);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                // Identity documents must not be kept in browser or proxy caches.
                .cacheControl(CacheControl.noStore())
                .body(content);
    }
}
