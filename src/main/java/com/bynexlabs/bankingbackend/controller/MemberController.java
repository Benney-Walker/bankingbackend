package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.KycRecordResponse;
import com.bynexlabs.bankingbackend.DTO.member.KycRejectionRequest;
import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.DTO.member.MemberProfileResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberSummaryResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.service.KycService;
import com.bynexlabs.bankingbackend.service.MemberService;
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
