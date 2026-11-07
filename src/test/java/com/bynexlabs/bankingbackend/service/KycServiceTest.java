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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.services.textract.TextractClient;
import software.amazon.awssdk.services.textract.model.AnalyzeIDDetections;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private TextractClient textractClient;

    @Mock
    private OutboxService outboxService;

    @Mock
    private MemberAccountService memberAccountService;

    private KycService kycService;

    private final UUID memberId = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private final MockMultipartFile file =
            new MockMultipartFile("file", "card.jpg", "image/jpeg", new byte[]{1, 2, 3});

    @BeforeEach
    void setUp() {
        kycService = new KycService(memberRepository, kycRepository, storageService, textractClient,
                outboxService, memberAccountService);
    }

    private Member member() {
        Member member = new Member();
        member.setId(memberId);
        member.setFirstName("Ama");
        member.setLastName("Mensah");
        return member;
    }

    private MemberKycRecord record(KycStatus status) {
        MemberKycRecord record = new MemberKycRecord();
        record.setId(recordId);
        record.setMember(member());
        record.setStatus(status);
        record.setDocumentUrl("kyc/" + memberId + "/doc.jpg");
        return record;
    }

    private static IdentityDocumentField field(String type, String value, float confidence) {
        return IdentityDocumentField.builder()
                .type(AnalyzeIDDetections.builder().text(type).build())
                .valueDetection(AnalyzeIDDetections.builder().text(value).confidence(confidence).build())
                .build();
    }

    private static Map<String, IdentityDocumentField> ocr(String first, String last, float confidence) {
        return Map.of("FIRST_NAME", field("FIRST_NAME", first, confidence),
                "LAST_NAME", field("LAST_NAME", last, confidence));
    }

    // --- submission ---

    @Test
    void unknownDocumentTypeIsRejectedBeforeUploading() {
        assertThatThrownBy(() -> kycService.submitKyc(memberId, "GHANA_CARD", file))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ECOWAS_CARD");
        verifyNoInteractions(storageService, kycRepository, outboxService);
    }

    @Test
    void unknownMemberIsRejectedBeforeUploading() throws Exception {
        when(memberRepository.findById(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.submitKyc(memberId, "voter_card", file))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storageService, outboxService);
    }

    @Test
    void submissionStoresAPendingRecordAndQueuesVerificationThroughTheOutbox() throws Exception {
        Member member = member();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(storageService.uploadKycDocument(file, memberId)).thenReturn("kyc/" + memberId + "/x.jpg");
        when(kycRepository.save(any(MemberKycRecord.class))).thenAnswer(invocation -> {
            MemberKycRecord saved = invocation.getArgument(0);
            saved.setId(recordId);
            return saved;
        });

        kycService.submitKyc(memberId, "voter_card", file);

        ArgumentCaptor<MemberKycRecord> saved = ArgumentCaptor.forClass(MemberKycRecord.class);
        verify(kycRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(saved.getValue().getDocumentType()).isEqualTo(KycDocumentType.VOTER_CARD);
        assertThat(saved.getValue().getDocumentUrl()).isEqualTo("kyc/" + memberId + "/x.jpg");
        assertThat(saved.getValue().getVerifiedAt()).isNull();
        verify(outboxService).enqueue(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.KYC_VERIFICATION_ROUTING_KEY,
                KycVerificationEvent.builder().kycRecordId(recordId).build());
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
                .contains(new KycService.VerificationTask(recordId, "kyc/" + memberId + "/doc.jpg"));
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
    void resultIsIgnoredIfRecordWasApprovedManuallyMeanwhile() {
        MemberKycRecord record = record(KycStatus.VERIFIED);
        when(kycRepository.lockById(recordId)).thenReturn(Optional.of(record));

        kycService.completeAutomatedVerification(recordId, ocr("Akosua", "Boateng", 97f));
        kycService.sendToManualReview(recordId, "OCR failed");

        assertThat(record.getStatus()).isEqualTo(KycStatus.VERIFIED);
        verify(kycRepository, never()).save(any());
    }

    // --- manual approval ---

    @Test
    void manualApprovalWithoutStaffIsUnauthorized() {
        assertThatThrownBy(() -> kycService.approveKycInfoManually(memberId, null))
                .isInstanceOf(UnauthorizedException.class);
        verifyNoInteractions(memberAccountService);
    }

    @Test
    void manualApprovalWithoutSubmissionIsAConflict() {
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member()));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.approveKycInfoManually(memberId, new Staff()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member has not submitted a KYC document yet");
        verifyNoInteractions(memberAccountService);
    }

    @Test
    void manualApprovalActivatesWithTheApprovingStaff() {
        Member member = member();
        MemberKycRecord record = record(KycStatus.MANUAL_REVIEW);
        Staff staff = new Staff();
        when(memberRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record));

        kycService.approveKycInfoManually(memberId, staff);

        verify(memberAccountService).activateMemberAccounts(eq(member), eq(record), eq(VerificationMethod.MANUAL), eq(staff));
    }
}
