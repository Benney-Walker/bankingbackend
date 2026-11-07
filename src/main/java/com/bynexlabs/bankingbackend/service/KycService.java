package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.Utility.UnauthorizedException;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.enums.VerificationMethod;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeIdRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeIdResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * KYC submission, automated verification steps and manual approval.
 *
 * Automated verification runs in three steps (driven by AsyncService) so that no database
 * transaction is open while R2 or the OCR provider is being called:
 * 1. startAutomatedVerification: short read-only transaction, returns what the OCR step needs;
 * 2. download + OCR, outside any transaction;
 * 3. completeAutomatedVerification / sendToManualReview: short transaction with the record locked.
 * Steps 1 and 3 only act on PENDING records, so a duplicate message or a manual approval that
 * happened in the meantime is left alone.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycService {

    private final MemberRepository memberRepository;

    private final MemberKycRecordRepo kycRepository;

    private final StorageService storageService;

    private final TextractClient textractClient;

    private final OutboxService outboxService;

    private final MemberAccountService memberAccountService;

    private static final float CONFIDENCE_THRESHOLD = 85.0f; // 85% confidence required

    /** What the OCR step needs from a KYC record. */
    public record VerificationTask(UUID kycRecordId, String documentKey) {
    }

    @Transactional
    public ResponseEntity<?> submitKyc(UUID memberId, String documentType, MultipartFile file) {
        // Check the request before uploading anything, so bad input leaves no orphaned file in R2.
        KycDocumentType type = parseDocumentType(documentType);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));

        String documentKey;
        try {
            documentKey = storageService.uploadKycDocument(file, memberId);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded KYC document", e);
        }

        MemberKycRecord kycRecord = new MemberKycRecord();
        kycRecord.setMember(member);
        kycRecord.setDocumentType(type);
        kycRecord.setStatus(KycStatus.PENDING);
        kycRecord.setCreatedAt(LocalDateTime.now());
        kycRecord.setDocumentUrl(documentKey);
        MemberKycRecord savedRecord = kycRepository.save(kycRecord);

        // Through the outbox: the verification message exists only if the record committed,
        // so the consumer can never look for a record that isn't saved yet.
        KycVerificationEvent kycEvent = KycVerificationEvent.builder()
                .kycRecordId(savedRecord.getId())
                .build();
        outboxService.enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY, kycEvent);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "reviewing Kyc information"
        ));
    }

    @Transactional
    public ResponseEntity<?> approveKycInfoManually(UUID memberId, Staff staff) {
        if (staff == null) {
            // JwtAuthenticationFilter already rejects inactive staff; this guards against a
            // staff record that disappeared mid-request.
            throw new UnauthorizedException("Staff account not found or inactive");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Could not find membership record to update"));
        MemberKycRecord kycRecord = kycRepository.findByMember_Id(memberId)
                .orElseThrow(() -> new ConflictException("This member has not submitted a KYC document yet"));

        memberAccountService.activateMemberAccounts(member, kycRecord, VerificationMethod.MANUAL, staff);

        return ResponseEntity.ok(Map.of(
                "status", "success"
        ));
    }

    // ----- Automated verification steps (see class comment) -----

    @Transactional(readOnly = true)
    public Optional<VerificationTask> startAutomatedVerification(UUID kycRecordId) {
        // With the outbox the record is always committed before its message is sent, so a
        // missing record is unexpected: throwing lets the message retry and then dead-letter.
        MemberKycRecord record = kycRepository.findById(kycRecordId)
                .orElseThrow(() -> new IllegalStateException("KYC record " + kycRecordId + " not found"));
        if (record.getStatus() != KycStatus.PENDING) {
            log.info("KYC record {} is already {}; skipping automated verification", kycRecordId, record.getStatus());
            return Optional.empty();
        }
        return Optional.of(new VerificationTask(record.getId(), extractKeyFromUrl(record.getDocumentUrl())));
    }

    @Transactional
    public void completeAutomatedVerification(UUID kycRecordId, Map<String, IdentityDocumentField> extractedData) {
        MemberKycRecord record = lockPendingRecord(kycRecordId);
        if (record != null) {
            verifyAndRoute(record, record.getMember(), extractedData);
        }
    }

    @Transactional
    public void sendToManualReview(UUID kycRecordId, String reason) {
        MemberKycRecord record = lockPendingRecord(kycRecordId);
        if (record != null) {
            record.setStatus(KycStatus.MANUAL_REVIEW);
            record.setRejectionReason(reason);
            kycRepository.save(record);
        }
    }

    private MemberKycRecord lockPendingRecord(UUID kycRecordId) {
        MemberKycRecord record = kycRepository.lockById(kycRecordId)
                .orElseThrow(() -> new IllegalStateException("KYC record " + kycRecordId + " not found"));
        if (record.getStatus() != KycStatus.PENDING) {
            log.info("KYC record {} became {} during automated verification; leaving it", kycRecordId, record.getStatus());
            return null;
        }
        return record;
    }

    //  -----Helpers----

    public Map<String, IdentityDocumentField> extractIdInformation(byte[] documentBytes) {
        Document document = Document.builder()
                .bytes(software.amazon.awssdk.core.SdkBytes.fromByteArray(documentBytes))
                .build();

        AnalyzeIdRequest request = AnalyzeIdRequest.builder()
                .documentPages(document)
                .build();

        AnalyzeIdResponse response = textractClient.analyzeID(request);

        // Map the extracted fields (FIRST_NAME, LAST_NAME, DATE_OF_BIRTH, etc.)
        return response.identityDocuments().get(0).identityDocumentFields().stream()
                .collect(Collectors.toMap(
                        field -> field.type().text(),
                        field -> field
                ));
    }

    // Matching rules are unchanged in 3.1 (first and last name at 85% confidence). Date of birth
    // and ID number matching come in roadmap 3.6.
    void verifyAndRoute(MemberKycRecord record, Member member, Map<String, IdentityDocumentField> extractedData) {
        IdentityDocumentField firstNameField = extractedData.get("FIRST_NAME");
        IdentityDocumentField lastNameField = extractedData.get("LAST_NAME");

        if (firstNameField == null || lastNameField == null ||
                firstNameField.valueDetection().confidence() < CONFIDENCE_THRESHOLD ||
                lastNameField.valueDetection().confidence() < CONFIDENCE_THRESHOLD) {

            record.setStatus(KycStatus.MANUAL_REVIEW);
            record.setRejectionReason("Automated OCR confidence too low. Image may be blurry or obscured.");
            kycRepository.save(record);
            return;
        }

        String extractedFirstName = firstNameField.valueDetection().text();
        String extractedLastName = lastNameField.valueDetection().text();

        boolean nameMatches = member.getFirstName().equalsIgnoreCase(extractedFirstName) &&
                member.getLastName().equalsIgnoreCase(extractedLastName);

        if (nameMatches) {
            memberAccountService.activateMemberAccounts(member, record, VerificationMethod.AUTOMATED, null);
        } else {
            record.setStatus(KycStatus.MANUAL_REVIEW);
            record.setRejectionReason(String.format("Name mismatch. Profile: %s %s, ID: %s %s",
                    member.getFirstName(), member.getLastName(), extractedFirstName, extractedLastName));
            kycRepository.save(record);
        }
    }

    public String extractKeyFromUrl(String url) {
        return url.substring(url.indexOf("kyc/"));
    }

    private KycDocumentType parseDocumentType(String documentType) {
        try {
            return KycDocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Document type must be one of " + Arrays.toString(KycDocumentType.values()));
        }
    }
}
