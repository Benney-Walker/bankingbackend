package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class KycService {

    private final MemberRepository memberRepository;

    private final MemberKycRecordRepo kycRepository;

    private final StorageService storageService;

    @Transactional
    public ResponseEntity<?> submitKyc(UUID memberId, String documentType, MultipartFile file) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        String documentUrl = null;
        try {
            documentUrl = storageService.uploadKycDocument(file, memberId);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 2. Save the KYC record
        MemberKycRecord kycRecord = new MemberKycRecord();
        kycRecord.setMember(member);
        kycRecord.setDocumentType(KycDocumentType.valueOf(documentType.toUpperCase()));
        kycRecord.setStatus(KycStatus.PENDING);
        kycRecord.setCreatedAt(LocalDateTime.now());
        kycRecord.setDocumentUrl(documentUrl);
        kycRepository.save(kycRecord);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "reviewing Kyc information"
        ));
    }
}
