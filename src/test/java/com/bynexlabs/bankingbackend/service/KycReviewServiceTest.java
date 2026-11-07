package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.KycRecordResponse;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ForbiddenException;
import com.bynexlabs.bankingbackend.Utility.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;

import java.util.List;
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

/**
 * Review list, record details and document access (roadmap 3.7). Owner rules: administrators see
 * all members' KYC; agents only members they registered.
 */
@ExtendWith(MockitoExtension.class)
class KycReviewServiceTest {

    private static final String AGENT_ID = "900105";
    private static final String OTHER_AGENT_ID = "900106";
    private static final String ADMIN_ID = "900100";

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

    @BeforeEach
    void setUp() {
        kycService = new KycService(memberRepository, kycRepository, storageService, outboxService, memberAccountService);
    }

    private MemberKycRecord record(String registeredByStaffId, String documentKey) {
        Member member = new Member();
        member.setId(memberId);
        member.setFirstName("Ama");
        member.setLastName("Mensah");
        member.setRegisteredBy(Staff.builder().id(UUID.randomUUID()).staffId(registeredByStaffId).build());
        MemberKycRecord record = new MemberKycRecord();
        record.setId(UUID.randomUUID());
        record.setMember(member);
        record.setStatus(KycStatus.MANUAL_REVIEW);
        record.setDocumentType(KycDocumentType.ECOWAS_CARD);
        record.setIdNumber("GHA-123456789-0");
        record.setRejectionReason("Details did not match: date of birth");
        record.setDocumentUrl(documentKey);
        return record;
    }

    // --- list ---

    @Test
    void administratorSeesEveryMembersRecordsOldestFirst() {
        when(kycRepository.findByStatus(eq(KycStatus.MANUAL_REVIEW), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record(AGENT_ID, "kyc/a.jpg"))));

        PagedModel<KycRecordResponse> result = kycService.listForReview(null, 0, 20, ADMIN_ID, true);

        assertThat(result.getContent()).extracting(KycRecordResponse::getMemberId).containsExactly(memberId);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(kycRepository).findByStatus(eq(KycStatus.MANUAL_REVIEW), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        verify(kycRepository, never()).findByStatusAndMember_RegisteredBy_StaffId(any(), anyString(), any());
    }

    @Test
    void agentOnlySeesMembersTheyRegistered() {
        when(kycRepository.findByStatusAndMember_RegisteredBy_StaffId(eq(KycStatus.REJECTED), eq(AGENT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(record(AGENT_ID, "kyc/a.jpg"))));

        PagedModel<KycRecordResponse> result = kycService.listForReview("rejected", 0, 20, AGENT_ID, false);

        assertThat(result.getContent()).hasSize(1);
        verify(kycRepository, never()).findByStatus(any(), any());
    }

    @Test
    void unknownStatusIsBadRequest() {
        assertThatThrownBy(() -> kycService.listForReview("WAITING", 0, 20, ADMIN_ID, true))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(kycRepository);
    }

    @Test
    void pageSizeAboveLimitIsBadRequest() {
        assertThatThrownBy(() -> kycService.listForReview(null, 0, KycService.MAX_PAGE_SIZE + 1, ADMIN_ID, true))
                .isInstanceOf(BadRequestException.class);
    }

    // --- details ---

    @Test
    void detailsShowTheReasonAndWhoRegisteredTheMember() {
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(AGENT_ID, "kyc/a.jpg")));

        KycRecordResponse details = kycService.getKycRecord(memberId, AGENT_ID, false);

        assertThat(details.getReason()).isEqualTo("Details did not match: date of birth");
        assertThat(details.getRegisteredByStaffId()).isEqualTo(AGENT_ID);
        assertThat(details.getIdNumber()).isEqualTo("GHA-123456789-0");
    }

    @Test
    void agentCantSeeAnotherAgentsMember() {
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(OTHER_AGENT_ID, "kyc/a.jpg")));

        assertThatThrownBy(() -> kycService.getKycRecord(memberId, AGENT_ID, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only view KYC records of members you registered");
    }

    @Test
    void administratorCanSeeAnyMember() {
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(OTHER_AGENT_ID, "kyc/a.jpg")));

        assertThat(kycService.getKycRecord(memberId, ADMIN_ID, true).getMemberId()).isEqualTo(memberId);
    }

    @Test
    void memberWithoutKycIsNotFound() {
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> kycService.getKycRecord(memberId, ADMIN_ID, true))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- document ---

    @Test
    void documentIsResolvedForAnAuthorisedViewerAndStreamedFromStorage() {
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(AGENT_ID, "kyc/m/doc.png")));
        when(storageService.downloadKycDocument("kyc/m/doc.png")).thenReturn(new byte[]{9, 9});

        KycService.KycDocumentRef document = kycService.findDocumentForViewer(memberId, AGENT_ID, false);
        byte[] content = kycService.downloadDocument(document);

        assertThat(document.contentType()).isEqualTo("image/png");
        assertThat(content).containsExactly(9, 9);
    }

    @Test
    void agentCantDownloadAnotherAgentsMembersDocument() {
        when(kycRepository.findByMember_Id(memberId)).thenReturn(Optional.of(record(OTHER_AGENT_ID, "kyc/m/doc.png")));

        assertThatThrownBy(() -> kycService.findDocumentForViewer(memberId, AGENT_ID, false))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(storageService);
    }

    @Test
    void contentTypeFollowsTheStoredExtension() {
        assertThat(KycService.contentTypeOf("kyc/m/x.JPG")).isEqualTo("image/jpeg");
        assertThat(KycService.contentTypeOf("kyc/m/x.jpeg")).isEqualTo("image/jpeg");
        assertThat(KycService.contentTypeOf("kyc/m/x.png")).isEqualTo("image/png");
        assertThat(KycService.contentTypeOf("kyc/m/x")).isEqualTo("application/octet-stream");
    }
}
