package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedIdDocument;
import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedValue;
import com.bynexlabs.bankingbackend.DTO.ocr.IdDocumentField;
import com.bynexlabs.bankingbackend.DTO.member.KycRecordResponse;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.KycUploadRules;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.ForbiddenException;
import com.bynexlabs.bankingbackend.Utility.IdNumberRules;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * KYC submission, automated verification steps and manual approval.
 *
 * Automated verification runs in three steps (driven by AsyncService) so that no database
 * transaction is open while R2 or the OCR provider is being called:
 * 1. startAutomatedVerification: short read-only transaction, returns what the OCR step needs;
 * 2. download + OCR (OcrInterface, provider-neutral), outside any transaction;
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

    private final OutboxService outboxService;

    private final MemberAccountService memberAccountService;

    private static final float CONFIDENCE_THRESHOLD = 85.0f; // 85% confidence required

    /** What the OCR step needs from a KYC record. */
    public record VerificationTask(UUID kycRecordId, String documentKey) {
    }

    /**
     * First submission, or re-upload after a rejection. Owner rules (D8, 2026-11-07): one KYC record
     * per member; no re-upload unless a manual review rejected the document, and then only by the
     * staff member who registered the member. The new document replaces the old one, and the old
     * file is deleted from R2 once the change has committed.
     */
    @Transactional
    public ResponseEntity<?> submitKyc(UUID memberId, String documentType, String idNumber, MultipartFile file, Staff uploader) {
        if (uploader == null) {
            throw new UnauthorizedException("Staff account not found or inactive");
        }
        // Check the request before uploading anything, so bad input leaves no orphaned file in R2.
        KycDocumentType type = parseDocumentType(documentType);
        String normalizedIdNumber = normalizeIdNumber(idNumber);
        IdNumberRules.requireValidFormat(type, normalizedIdNumber);
        KycUploadRules.ImageType imageType = KycUploadRules.validate(file);
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member not found"));
        if (kycRepository.existsByIdNumberAndMember_IdNot(normalizedIdNumber, memberId)) {
            throw new ConflictException("This ID number is already registered to another member");
        }

        MemberKycRecord existing = kycRepository.findByMember_Id(memberId).orElse(null);
        if (existing != null) {
            requireAllowedReupload(existing, member, uploader);
        }

        String documentKey = upload(file, memberId, imageType);

        MemberKycRecord kycRecord;
        String replacedKey = null;
        if (existing == null) {
            kycRecord = new MemberKycRecord();
            kycRecord.setMember(member);
            kycRecord.setCreatedAt(LocalDateTime.now());
        } else {
            // Lock and re-check: another re-upload or review may have happened since the check above.
            kycRecord = kycRepository.lockById(existing.getId()).orElseThrow();
            if (kycRecord.getStatus() != KycStatus.REJECTED) {
                storageService.deleteKycDocument(documentKey);
                throw new ConflictException("This member's KYC document is no longer awaiting a re-upload");
            }
            replacedKey = kycRecord.getDocumentUrl();
            kycRecord.setRejectionReason(null);
            kycRecord.setVerificationMethod(null);
            kycRecord.setStaff(null);
        }
        kycRecord.setDocumentType(type);
        kycRecord.setIdNumber(normalizedIdNumber);
        kycRecord.setStatus(KycStatus.PENDING);
        kycRecord.setDocumentUrl(documentKey);

        MemberKycRecord savedRecord;
        try {
            // Flush now so a simultaneous first upload hits the unique constraint here, as a 409.
            savedRecord = kycRepository.saveAndFlush(kycRecord);
        } catch (DataIntegrityViolationException e) {
            // A simultaneous submission for the same member, or the same ID number for another
            // member, reached the database constraint first (V19 / V21).
            throw new ConflictException("A KYC record for this member or this ID number already exists");
        }

        // Through the outbox: the verification message exists only if the record committed,
        // so the consumer can never look for a record that isn't saved yet.
        KycVerificationEvent kycEvent = KycVerificationEvent.builder()
                .kycRecordId(savedRecord.getId())
                .build();
        outboxService.enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY, kycEvent);

        if (replacedKey != null) {
            deleteAfterCommit(replacedKey);
        }

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "reviewing Kyc information"
        ));
    }

    private void requireAllowedReupload(MemberKycRecord existing, Member member, Staff uploader) {
        if (existing.getStatus() != KycStatus.REJECTED) {
            throw new ConflictException("A KYC document has already been submitted for this member");
        }
        Staff registeredBy = member.getRegisteredBy();
        if (registeredBy == null || !registeredBy.getId().equals(uploader.getId())) {
            // Members registered before V20 have no registering staff recorded, so nobody passes
            // this check for them yet; the owner is deciding who may re-upload in that case.
            throw new ForbiddenException(
                    "Only the staff member who registered this member can re-upload their KYC document");
        }
    }

    private String upload(MultipartFile file, UUID memberId, KycUploadRules.ImageType imageType) {
        try {
            return storageService.uploadKycDocument(file, memberId, imageType);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded KYC document", e);
        }
    }

    // R2 isn't part of the database transaction, so the replaced file is removed only after the
    // new record has committed. If deletion fails, the cost is an unused private file, logged.
    private void deleteAfterCommit(String objectKey) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storageService.deleteKycDocument(objectKey);
                } catch (RuntimeException e) {
                    log.warn("Could not delete replaced KYC document {}", objectKey, e);
                }
            }
        });
    }

    /** Owner rule (2026-11-07): only an ADMINISTRATOR approves or rejects KYC manually. */
    @Transactional
    public ResponseEntity<?> approveKycInfoManually(UUID memberId, Staff staff) {
        if (staff == null) {
            // JwtAuthenticationFilter already rejects inactive staff; this guards against a
            // staff record that disappeared mid-request.
            throw new UnauthorizedException("Staff account not found or inactive");
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Could not find membership record to update"));
        MemberKycRecord kycRecord = reviewableRecord(memberId);

        memberAccountService.activateMemberAccounts(member, kycRecord, VerificationMethod.MANUAL, staff);

        return ResponseEntity.ok(Map.of(
                "status", "success"
        ));
    }

    /**
     * Rejects the document; the member stays PENDING and their accounts inactive. The staff member
     * who registered the member can then upload a clearer document.
     */
    @Transactional
    public ResponseEntity<?> rejectKycManually(UUID memberId, String reason, Staff staff) {
        if (staff == null) {
            throw new UnauthorizedException("Staff account not found or inactive");
        }
        memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Could not find membership record to update"));
        MemberKycRecord kycRecord = reviewableRecord(memberId);

        kycRecord.setStatus(KycStatus.REJECTED);
        kycRecord.setRejectionReason(reason);
        kycRecord.setVerificationMethod(VerificationMethod.MANUAL);
        // verified_by holds the reviewing administrator for either outcome.
        kycRecord.setStaff(staff);
        kycRepository.save(kycRecord);

        return ResponseEntity.ok(Map.of(
                "status", "success"
        ));
    }

    // Only a document still under review can be approved or rejected. The record is locked so a
    // review can't overlap with the automated check writing its result.
    private MemberKycRecord reviewableRecord(UUID memberId) {
        MemberKycRecord found = kycRepository.findByMember_Id(memberId)
                .orElseThrow(() -> new ConflictException("This member has not submitted a KYC document yet"));
        MemberKycRecord record = kycRepository.lockById(found.getId()).orElseThrow();
        if (record.getStatus() != KycStatus.PENDING && record.getStatus() != KycStatus.MANUAL_REVIEW) {
            throw new ConflictException("This member's KYC is " + record.getStatus() + " and can't be reviewed");
        }
        return record;
    }

    // ----- Review list, record details and document viewing (roadmap 3.7) -----
    // Owner rules (2026-11-07): administrators see every member's KYC; agents only members they
    // registered. Approving and rejecting stays administrator-only (above).

    static final int MAX_PAGE_SIZE = 100;

    /** Where a viewer may fetch a document from; the object key never leaves the backend. */
    public record KycDocumentRef(UUID memberId, String objectKey, String contentType) {
    }

    @Transactional(readOnly = true)
    public PagedModel<KycRecordResponse> listForReview(String status, int page, int size,
                                                       String viewerStaffId, boolean viewerIsAdmin) {
        KycStatus kycStatus = parseStatus(status);
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        // Oldest first, so the review list works as a queue.
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt"));
        Page<MemberKycRecord> records = viewerIsAdmin
                ? kycRepository.findByStatus(kycStatus, pageRequest)
                : kycRepository.findByStatusAndMember_RegisteredBy_StaffId(kycStatus, viewerStaffId, pageRequest);
        return new PagedModel<>(records.map(KycRecordResponse::from));
    }

    @Transactional(readOnly = true)
    public KycRecordResponse getKycRecord(UUID memberId, String viewerStaffId, boolean viewerIsAdmin) {
        return KycRecordResponse.from(visibleRecord(memberId, viewerStaffId, viewerIsAdmin));
    }

    /**
     * Authorises the viewer and resolves the document, in a short read-only transaction. The
     * caller then downloads it with downloadDocument, outside any transaction.
     */
    @Transactional(readOnly = true)
    public KycDocumentRef findDocumentForViewer(UUID memberId, String viewerStaffId, boolean viewerIsAdmin) {
        MemberKycRecord record = visibleRecord(memberId, viewerStaffId, viewerIsAdmin);
        String key = record.getDocumentUrl();
        // Identity documents are sensitive: record who looked at which member's document.
        log.info("KYC document of member {} viewed by staff {}", memberId, viewerStaffId);
        return new KycDocumentRef(memberId, key, contentTypeOf(key));
    }

    public byte[] downloadDocument(KycDocumentRef document) {
        return storageService.downloadKycDocument(document.objectKey());
    }

    private MemberKycRecord visibleRecord(UUID memberId, String viewerStaffId, boolean viewerIsAdmin) {
        MemberKycRecord record = kycRepository.findByMember_Id(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("No KYC record for this member"));
        if (!viewerIsAdmin) {
            Staff registeredBy = record.getMember().getRegisteredBy();
            if (registeredBy == null || viewerStaffId == null || !viewerStaffId.equals(registeredBy.getStaffId())) {
                throw new ForbiddenException("You can only view KYC records of members you registered");
            }
        }
        return record;
    }

    // Keys are written with the extension of the detected image type (KycUploadRules).
    static String contentTypeOf(String objectKey) {
        String key = objectKey.toLowerCase();
        if (key.endsWith(".png")) {
            return "image/png";
        }
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static KycStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return KycStatus.MANUAL_REVIEW;
        }
        try {
            return KycStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status must be one of " + Arrays.toString(KycStatus.values()));
        }
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
    public void completeAutomatedVerification(UUID kycRecordId, ExtractedIdDocument extracted) {
        MemberKycRecord record = lockPendingRecord(kycRecordId);
        if (record != null) {
            verifyAndRoute(record, record.getMember(), extracted);
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

    /**
     * Owner rules (2026-11-07): first name, last name, date of birth and ID number must each be read
     * with at least 85% confidence and must all match what was entered, or the record goes to
     * manual review. Nothing is guessed: an unreadable or ambiguous value is treated as no match.
     * OCR matching confirms the document's text agrees with the entered details; it does not prove
     * the document is genuine.
     */
    void verifyAndRoute(MemberKycRecord record, Member member, ExtractedIdDocument extracted) {
        List<String> unreadable = new ArrayList<>();
        ExtractedValue firstName = confidentValue(extracted, IdDocumentField.FIRST_NAME, "first name", unreadable);
        ExtractedValue lastName = confidentValue(extracted, IdDocumentField.LAST_NAME, "last name", unreadable);
        ExtractedValue dateOfBirth = confidentValue(extracted, IdDocumentField.DATE_OF_BIRTH, "date of birth", unreadable);
        ExtractedValue idNumber = confidentValue(extracted, IdDocumentField.DOCUMENT_NUMBER, "ID number", unreadable);
        if (!unreadable.isEmpty()) {
            sendRecordToManualReview(record, "Automated OCR could not read with enough confidence: "
                    + String.join(", ", unreadable) + ". Image may be blurry or obscured.");
            return;
        }

        List<String> mismatches = new ArrayList<>();
        if (!sameText(member.getFirstName(), firstName.text())) {
            mismatches.add(String.format("first name (profile '%s', document '%s')", member.getFirstName(), firstName.text()));
        }
        if (!sameText(member.getLastName(), lastName.text())) {
            mismatches.add(String.format("last name (profile '%s', document '%s')", member.getLastName(), lastName.text()));
        }
        LocalDate documentDob = isoDate(dateOfBirth.normalizedText());
        if (documentDob == null) {
            mismatches.add(String.format("date of birth (document '%s' could not be read as a date)", dateOfBirth.text()));
        } else if (!documentDob.equals(member.getDateOfBirth())) {
            mismatches.add(String.format("date of birth (profile %s, document %s)", member.getDateOfBirth(), documentDob));
        }
        if (record.getIdNumber() == null || !sameIdNumber(record.getIdNumber(), idNumber.text())) {
            mismatches.add(String.format("ID number (entered '%s', document '%s')", record.getIdNumber(), idNumber.text()));
        }

        if (mismatches.isEmpty()) {
            memberAccountService.activateMemberAccounts(member, record, VerificationMethod.AUTOMATED, null);
        } else {
            sendRecordToManualReview(record, "Details did not match: " + String.join("; ", mismatches));
        }
    }

    private static ExtractedValue confidentValue(ExtractedIdDocument extracted, IdDocumentField field,
                                                 String label, List<String> unreadable) {
        ExtractedValue value = extracted.get(field).orElse(null);
        if (value == null || value.text() == null || value.text().isBlank() || value.confidence() < CONFIDENCE_THRESHOLD) {
            unreadable.add(label);
            return null;
        }
        return value;
    }

    private void sendRecordToManualReview(MemberKycRecord record, String reason) {
        record.setStatus(KycStatus.MANUAL_REVIEW);
        record.setRejectionReason(reason);
        kycRepository.save(record);
    }

    private static boolean sameText(String entered, String read) {
        return entered != null && entered.trim().equalsIgnoreCase(read.trim());
    }

    // Typed numbers are stored trimmed and upper-cased; spaces OCR may insert are ignored too.
    private static boolean sameIdNumber(String entered, String read) {
        return entered.replaceAll("\\s", "").equalsIgnoreCase(read.replaceAll("\\s", ""));
    }

    // Only the provider's ISO-normalised date is trusted (see ExtractedValue); anything else is null.
    private static LocalDate isoDate(String normalized) {
        if (normalized == null || normalized.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(normalized.substring(0, 10));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public String extractKeyFromUrl(String url) {
        return url.substring(url.indexOf("kyc/"));
    }

    // Stored trimmed and upper-cased so the uniqueness check and the comparison with OCR don't
    // depend on how staff typed it. Per-card-type formats are checked by IdNumberRules.
    static String normalizeIdNumber(String idNumber) {
        if (idNumber == null || idNumber.isBlank()) {
            throw new BadRequestException("ID number is required");
        }
        String normalized = idNumber.trim().toUpperCase();
        if (normalized.length() > 50) {
            throw new BadRequestException("ID number must be at most 50 characters");
        }
        return normalized;
    }

    private KycDocumentType parseDocumentType(String documentType) {
        try {
            return KycDocumentType.valueOf(documentType.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Document type must be one of " + Arrays.toString(KycDocumentType.values()));
        }
    }
}
