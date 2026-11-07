package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedIdDocument;
import com.bynexlabs.bankingbackend.DTO.ocr.ExtractedValue;
import com.bynexlabs.bankingbackend.DTO.ocr.IdDocumentField;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.ForbiddenException;
import com.bynexlabs.bankingbackend.Utility.KycUploadRules;
import com.bynexlabs.bankingbackend.Utility.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.Utility.UnauthorizedException;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.enums.VerificationMethod;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KycServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberKycRecordRepo kycRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MemberAccountService memberAccountService;

    private KycService kycService;

    private final UUID memberId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();

    // Starts with the JPEG signature, so it passes KycUploadRules.
    private static final byte[] JPEG_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    private final MockMultipartFile file = new MockMultipartFile("file", "card.jpg", "image/jpeg", JPEG_BYTES);

    private final Staff registrar = staff("900105");
    private final Staff otherAgent = staff("900106");
    private final Staff admin = staff("900100");

    @BeforeEach
    void setUp() {
        kycService = new KycService(memberRepository, kycRepository, storageService,
                outboxService, memberAccountService);
        // Stand-in for the request transaction, so after-commit callbacks can be registered.
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    private static Staff staff(String staffId) {
        return Staff.builder().id(UUID.randomUUID()).staffId(staffId).build();
    }

    private Member member(Staff registeredBy) {
        Member member = new Member();
        member.setId(memberId);
        member.setFirstName("Ama");
        member.setLastName("Mensah");
        member.setRegisteredBy(registeredBy);
        return member;
    }

    private MemberKycRecord record(KycStatus status) {
        MemberKycRecord record = new MemberKycRecord();
        record.setId(recordId);
        record.setMember(member(registrar));
        record.setStatus(status);
        record.setDocumentUrl("kyc/" + memberId + "/old.jpg");
        return record;
    }

    private static ExtractedIdDocument ocr(String first, String last, float confidence) {
        return new ExtractedIdDocument(Map.of(
                IdDocumentField.FIRST_NAME, new ExtractedValue(first, confidence),
                IdDocumentField.LAST_NAME, new ExtractedValue(last, confidence)));
    }

    private void givenSaveAndFlushAssignsId() {
        when(kycRepository.saveAndFlush(any(MemberKycRecord.class))).thenAnswer(invocation -> {
            MemberKycRecord saved = invocation.getArgument(0);
            saved.setId(recordId);
            return saved;
        });
    }

    // --- first submission ---

    @Test
    void unknownDocumentTypeIsRejectedBeforeUploading() {
        assertThatThrownBy(() -> kycService.submitKyc(memberId, "GHANA_CARD", file, registrar))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ECOWAS_CARD");
        verifyNoInteractions(storageService, kycRepository, outboxService);
    }

    @Test
    void nonImageFileIsRejectedBeforeAnyDatabaseWork() {
        MockMultipartFile pdf = new MockMultipartFile("file", "card.jpg", "image/jpeg", "%PDF-1.7".getBytes());

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "VOTER_CARD", pdf, registrar))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("The KYC document must be a JPEG or PNG image");
        verifyNoInteractions(memberRepository, kycRepository, storageService);
    }

    @Test
    void unknownMemberIsRejectedBeforeUploading() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "voter_card", file, registrar))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storageService, outboxService);
    }

    @Test
    void firstSubmissionStoresAPendingRecordAndQueuesVerificationThroughTheOutbox() throws Exception {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(storageService.uploadKycDocument(file, memberId, KycUploadRules.ImageType.JPEG))
                .thenReturn("kyc/" + memberId + "/x.jpg");
        givenSaveAndFlushAssignsId();

        kycService.submitKyc(memberId, "voter_card", file, otherAgent);

        ArgumentCaptor<MemberKycRecord> saved = ArgumentCaptor.forClass(MemberKycRecord.class);
        verify(kycRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(saved.getValue().getDocumentType()).isEqualTo(KycDocumentType.VOTER_CARD);
        assertThat(saved.getValue().getDocumentUrl()).isEqualTo("kyc/" + memberId + "/x.jpg");
        assertThat(saved.getValue().getVerifiedAt()).isNull();
        verify(outboxService).enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY,
                KycVerificationEvent.builder().kycRecordId(recordId).build());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void simultaneousFirstSubmissionHittingTheConstraintIsAConflict() throws Exception {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(storageService.uploadKycDocument(file, memberId, KycUploadRules.ImageType.JPEG)).thenReturn("kyc/k.jpg");
        when(kycRepository.saveAndFlush(any(MemberKycRecord.class)))
                .thenThrow(new DataIntegrityViolationException("uq_member_kyc_records_member"));

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "VOTER_CARD", file, registrar))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(outboxService);
    }

    // --- re-upload rules (D8) ---

    @Test
    void reuploadIsRefusedUnlessTheDocumentWasRejected() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(KycStatus.MANUAL_REVIEW)));

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "VOTER_CARD", file, registrar))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A KYC document has already been submitted for this member");
        verifyNoInteractions(storageService, outboxService);
    }

    @Test
    void onlyTheRegisteringStaffMemberCanReuploadAfterRejection() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(KycStatus.REJECTED)));

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "VOTER_CARD", file, otherAgent))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(storageService, outboxService);
    }

    @Test
    void memberWithoutRecordedRegistrarCantBeReuploadedYet() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(null)));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(KycStatus.REJECTED)));

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "VOTER_CARD", file, admin))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void reuploadByRegistrarReplacesTheDocumentAndDeletesTheOldFileAfterCommit() throws Exception {
        MemberKycRecord rejected = record(KycStatus.REJECTED);
        rejected.setRejectionReason("Photo is blurry");
        rejected.setVerificationMethod(VerificationMethod.MANUAL);
        rejected.setStaff(admin);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(rejected));
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(rejected));
        when(storageService.uploadKycDocument(file, memberId, KycUploadRules.ImageType.JPEG))
                .thenReturn("kyc/" + memberId + "/new.jpg");
        givenSaveAndFlushAssignsId();

        kycService.submitKyc(memberId, "ECOWAS_CARD", file, registrar);

        assertThat(rejected.getStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(rejected.getDocumentUrl()).isEqualTo("kyc/" + memberId + "/new.jpg");
        assertThat(rejected.getDocumentType()).isEqualTo(KycDocumentType.ECOWAS_CARD);
        assertThat(rejected.getRejectionReason()).isNull();
        assertThat(rejected.getStaff()).isNull();
        verify(outboxService).enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY,
                KycVerificationEvent.builder().kycRecordId(recordId).build());

        // The old file goes only after commit.
        verify(storageService, never()).deleteKycDocument(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(storageService).deleteKycDocument("kyc/" + memberId + "/old.jpg");
    }

    // --- automated verification steps ---

    @Test
    void onlyPendingRecordsStartAutomatedVerification() {
        when(kycRepository.findById(recordId)).thenReturn(Optional.of(record(KycStatus.VERIFIED)));

        assertThat(kycService.startAutomatedVerification(recordId)).isEmpty();
    }

    @Test
    void pendingRecordGivesTheDocumentKey() {
        when(kycRepository.findById(recordId)).thenReturn(Optional.of(record(KycStatus.PENDING)));

        assertThat(kycService.startAutomatedVerification(recordId))
                .contains(new KycService.VerificationTask(recordId, "kyc/" + memberId + "/old.jpg"));
    }

    @Test
    void missingRecordThrowsSoTheMessageIsRetriedAndDeadLettered() {
        when(kycRepository.findById(recordId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.startAutomatedVerification(recordId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void matchingNamesActivateAutomatically() {
        MemberKycRecord record = record(KycStatus.PENDING);
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.completeAutomatedVerification(recordId, ocr("AMA", "mensah", 97f));

        verify(memberAccountService).activateMemberAccounts(record.getMember(), record, VerificationMethod.AUTOMATED, null);
    }

    @Test
    void nameMismatchGoesToManualReview() {
        MemberKycRecord record = record(KycStatus.PENDING);
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.completeAutomatedVerification(recordId, ocr("Akosua", "Mensah", 97f));

        assertThat(record.getStatus()).isEqualTo(KycStatus.MANUAL_REVIEW);
        assertThat(record.getRejectionReason()).startsWith("Name mismatch");
        verify(memberAccountService, never()).activateMemberAccounts(any(), any(), any(), any());
    }

    @Test
    void lowConfidenceGoesToManualReview() {
        MemberKycRecord record = record(KycStatus.PENDING);
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.completeAutomatedVerification(recordId, ocr("Ama", "Mensah", 60f));

        assertThat(record.getStatus()).isEqualTo(KycStatus.MANUAL_REVIEW);
        verify(memberAccountService, never()).activateMemberAccounts(any(), any(), any(), any());
    }

    @Test
    void resultIsIgnoredIfRecordWasReviewedMeanwhile() {
        MemberKycRecord record = record(KycStatus.REJECTED);
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.completeAutomatedVerification(recordId, ocr("Ama", "Mensah", 97f));
        kycService.sendToManualReview(recordId, "OCR failed");

        assertThat(record.getStatus()).isEqualTo(KycStatus.REJECTED);
        verify(kycRepository, never()).save(any());
        verifyNoInteractions(memberAccountService);
    }

    // --- manual review (ADMINISTRATOR only, enforced on the controller) ---

    @Test
    void manualApprovalWithoutStaffIsUnauthorized() {
        assertThatThrownBy(() -> kycService.approveKycInfoManually(memberId, null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(memberAccountService);
    }

    @Test
    void manualApprovalWithoutSubmissionIsAConflict() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.approveKycInfoManually(memberId, admin))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member has not submitted a KYC document yet");
        verifyNoInteractions(memberAccountService);
    }

    @Test
    void manualApprovalActivatesWithTheApprovingAdministrator() {
        Member member = member(registrar);
        MemberKycRecord record = record(KycStatus.MANUAL_REVIEW);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record));
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.approveKycInfoManually(memberId, admin);

        verify(memberAccountService).activateMemberAccounts(eq(member), eq(record), eq(VerificationMethod.MANUAL), eq(admin));
    }

    @Test
    void rejectedOrVerifiedDocumentsCantBeReviewedAgain() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        MemberKycRecord rejected = record(KycStatus.REJECTED);
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(rejected));
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> kycService.approveKycInfoManually(memberId, admin))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> kycService.rejectKycManually(memberId, "Blurry", admin))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(memberAccountService);
    }

    @Test
    void manualRejectionRecordsReasonAndReviewerAndLeavesMemberInactive() {
        MemberKycRecord record = record(KycStatus.MANUAL_REVIEW);
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member(registrar)));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record));
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.rejectKycManually(memberId, "Photo is blurry; date of birth unreadable", admin);

        assertThat(record.getStatus()).isEqualTo(KycStatus.REJECTED);
        assertThat(record.getRejectionReason()).isEqualTo("Photo is blurry; date of birth unreadable");
        assertThat(record.getStaff()).isSameAs(admin);
        assertThat(record.getVerifiedAt()).isNull();
        verify(kycRepository).save(record);
        verifyNoInteractions(memberAccountService);
    }
}
