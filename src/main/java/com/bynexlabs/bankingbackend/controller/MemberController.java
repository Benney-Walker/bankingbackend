package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.MemberDetails;
import com.bynexlabs.bankingbackend.service.KycService;
import com.bynexlabs.bankingbackend.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/member")
public class MemberController {

    private final MemberService memberService;

    private final KycService kycService;

    @PostMapping("/v1/add-member")
    public ResponseEntity<?> addNewMember(@Valid @RequestBody MemberDetails memberDetails) {
        return memberService.registerNewMember(memberDetails);
    }

    @PostMapping(
            value = "v1/kyc-document-upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> submitKyc(@RequestParam("memberId") UUID memberId,
                                       @RequestParam("documentType") String documentType,
                                       @RequestParam("file") MultipartFile file) {

        return kycService.submitKyc(memberId, documentType, file);
    }
}
