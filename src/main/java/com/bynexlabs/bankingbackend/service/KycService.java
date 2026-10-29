package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.enums.VerificationMethod;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import com.bynexlabs.bankingbackend.rabbitMQ.RabbitMQProducer;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeIdRequest;
import software.amazon.awssdk.services.textract.model.AnalyzeIdResponse;
import software.amazon.awssdk.services.textract.model.Document;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycService {

    private final MemberRepository memberRepository;

    private final MemberKycRecordRepo kycRepository;

    private final StorageService storageService;

    private final S3Client s3Client;

    private final TextractClient textractClient;

    private final RabbitMQProducer rabbitMQProducer;


    @Value("${cloudflare.r2.bucket-name}")
    private static String BUCKET_NAME;
    private static final float CONFIDENCE_THRESHOLD = 85.0f; // 85% confidence required

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
        MemberKycRecord savedRecord = kycRepository.save(kycRecord);

        KycVerificationEvent kycEvent = KycVerificationEvent.builder()
                .kycRecordId(savedRecord.getId())
                .build();
        rabbitMQProducer.sendKycVerificationEvent(kycEvent);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "reviewing Kyc information"
        ));
    }

    public byte[] downloadDocumentFromR2(String objectKey) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(BUCKET_NAME)
                .key(objectKey)
                .build();

        ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(getObjectRequest);
        return objectBytes.asByteArray();
    }

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

    public void verifyAndRoute(MemberKycRecord record, Member member, Map<String, IdentityDocumentField> extractedData) {
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

        // Check 2: Does the data match the Member's profile?
        boolean nameMatches = member.getFirstName().equalsIgnoreCase(extractedFirstName) &&
                member.getLastName().equalsIgnoreCase(extractedLastName);

        if (nameMatches) {
            record.setStatus(KycStatus.VERIFIED);
            record.setVerifiedAt(LocalDateTime.now());
            record.setVerificationMethod(VerificationMethod.AUTOMATED);
        } else {
            record.setStatus(KycStatus.MANUAL_REVIEW);
            record.setRejectionReason(String.format("Name mismatch. Profile: %s %s, ID: %s %s",
                    member.getFirstName(), member.getLastName(), extractedFirstName, extractedLastName));
        }

        kycRepository.save(record);
    }

    public String extractKeyFromUrl(String url) {
        return url.substring(url.indexOf("kyc/"));
    }
}
