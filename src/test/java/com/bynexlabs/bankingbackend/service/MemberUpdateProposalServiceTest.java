package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateProposalResponse;
import com.bynexlabs.bankingbackend.DTO.member.MemberUpdateRequest;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.MemberSmsEvent;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.Repository.MemberUpdateProposalRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.MemberUpdateStatus;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberUpdateProposal;
import com.bynexlabs.bankingbackend.models.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberUpdateProposalServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 14, 30);

    @Mock
    private MemberUpdateProposalRepo proposalRepo;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private MemberService memberService;
    @Mock
    private StaffRepo staffRepo;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private OutboxService outboxService;

    private MemberUpdateProposalService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900106").build();
    private final Staff admin = Staff.builder().id(UUID.randomUUID()).staffId("900101").build();
    private final Member member = member();

    @BeforeEach
    void setUp() {
        service = new MemberUpdateProposalService(proposalRepo, memberRepository, memberService, staffRepo,
                auditLogService, outboxService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        lenient().when(staffRepo.getReferenceById(agent.getId())).thenReturn(agent);
        lenient().when(staffRepo.getReferenceById(admin.getId())).thenReturn(admin);
        lenient().when(proposalRepo.save(any(MemberUpdateProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Member member() {
        Member m = new Member();
        m.setId(UUID.randomUUID());
        m.setFirstName("Ama");
        m.setLastName("Mensah");
        m.setDateOfBirth(LocalDate.of(1990, 5, 14));
        m.setPhoneNumber("0241234567");
        m.setResidentialAddress("Tarkwa");
        m.setOccupation("Trader");
        m.setBranch(tarkwa);
        return m;
    }

    private static MemberUpdateRequest change(String phone, String address, String occupation) {
        return MemberUpdateRequest.builder().phoneNumber(phone).residentialAddress(address).occupation(occupation).build();
    }

    private MemberUpdateProposal pending(Staff by) {
        MemberUpdateProposal proposal = MemberUpdateProposal.builder().id(UUID.randomUUID()).member(member)
                .requestedBy(by).status(MemberUpdateStatus.PENDING).phoneNumber("0249999999")
                .residentialAddress("Takoradi").occupation("Seamstress").requestedAt(NOW.minusHours(1)).build();
        lenient().when(proposalRepo.lockById(proposal.getId())).thenReturn(Optional.of(proposal));
        return proposal;
    }

    // --- proposing --------------------------------------------------------------------------

    @Test
    void agentProposesAChangeThatWaitsAndChangesNothingYet() {
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        MemberUpdateProposalResponse response = service.propose(member.getId(),
                change("0249999999", "Tarkwa", "Seamstress"), agent);

        assertThat(response.status()).isEqualTo(MemberUpdateStatus.PENDING);
        assertThat(response.current().phoneNumber()).isEqualTo("0241234567");
        assertThat(response.proposed().phoneNumber()).isEqualTo("0249999999");
        assertThat(response.requestedByStaffId()).isEqualTo("900106");
        assertThat(member.getPhoneNumber()).isEqualTo("0241234567");
        verify(auditLogService).record(eq(AuditAction.MEMBER_UPDATE_REQUESTED), eq(AuditTargetType.MEMBER), eq(member.getId()),
                org.mockito.ArgumentMatchers.endsWith("proposed: phoneNumber, occupation"));
        verifyNoInteractions(memberService, outboxService);
    }

    @Test
    void secondWaitingProposalForAMemberIsRefused() {
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(proposalRepo.existsByMember_IdAndStatus(member.getId(), MemberUpdateStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.propose(member.getId(), change("0249999999", "Tarkwa", "Trader"), agent))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This member already has a change waiting for approval");
        verify(proposalRepo, never()).save(any());
    }

    @Test
    void proposalWithNoChangeIsRefused() {
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.propose(member.getId(), change("0241234567", "Tarkwa", "Trader"), agent))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void phoneNumberOfAnotherMemberIsRefused() {
        when(memberRepository.findById(member.getId())).thenReturn(Optional.of(member));
        when(memberRepository.existsByPhoneNumberAndIdNot("0249999999", member.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.propose(member.getId(), change("0249999999", "Tarkwa", "Trader"), agent))
                .isInstanceOf(ConflictException.class);
    }

    // --- reviewing --------------------------------------------------------------------------

    @Test
    void approvalAppliesTheChangeAndTextsTheMember() {
        MemberUpdateProposal proposal = pending(agent);

        MemberUpdateProposalResponse response = service.approve(proposal.getId(), admin);

        ArgumentCaptor<MemberUpdateRequest> applied = ArgumentCaptor.forClass(MemberUpdateRequest.class);
        verify(memberService).updateMemberContact(eq(member.getId()), applied.capture());
        assertThat(applied.getValue().getPhoneNumber()).isEqualTo("0249999999");
        assertThat(applied.getValue().getOccupation()).isEqualTo("Seamstress");
        assertThat(response.status()).isEqualTo(MemberUpdateStatus.APPROVED);
        assertThat(response.reviewedByStaffId()).isEqualTo("900101");
        assertThat(proposal.getReviewedAt()).isEqualTo(NOW);

        ArgumentCaptor<MemberSmsEvent> sms = ArgumentCaptor.forClass(MemberSmsEvent.class);
        verify(outboxService).enqueue(eq(RabbitMQConfig.EXCHANGE_NAME), eq(RabbitMQConfig.MEMBER_SMS_ROUTING_KEY), sms.capture());
        assertThat(sms.getValue().getPhoneNumber()).isEqualTo("0249999999");
        assertThat(sms.getValue().getMessage()).isEqualTo(MemberUpdateProposalService.DETAILS_UPDATED_SMS);
        verify(auditLogService).record(AuditAction.MEMBER_UPDATE_APPROVED, AuditTargetType.MEMBER, member.getId(),
                "Request " + proposal.getId());
    }

    @Test
    void rejectionNeedsAReasonAndChangesNothing() {
        MemberUpdateProposal proposal = pending(agent);

        assertThatThrownBy(() -> service.reject(proposal.getId(), " ", admin)).isInstanceOf(BadRequestException.class);

        MemberUpdateProposalResponse response = service.reject(proposal.getId(), "Phone number not confirmed", admin);

        assertThat(response.status()).isEqualTo(MemberUpdateStatus.REJECTED);
        assertThat(response.rejectionReason()).isEqualTo("Phone number not confirmed");
        verifyNoInteractions(memberService, outboxService);
    }

    @Test
    void nobodyReviewsTheirOwnProposal() {
        MemberUpdateProposal proposal = pending(admin);

        assertThatThrownBy(() -> service.approve(proposal.getId(), admin))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can't review your own request");
        verifyNoInteractions(memberService, outboxService);
    }

    @Test
    void reviewedProposalCantBeReviewedAgain() {
        MemberUpdateProposal proposal = pending(agent);
        proposal.setStatus(MemberUpdateStatus.REJECTED);

        assertThatThrownBy(() -> service.approve(proposal.getId(), admin))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This request was already rejected");
    }

    @Test
    void phoneTakenSinceTheProposalKeepsItWaiting() {
        MemberUpdateProposal proposal = pending(agent);
        when(memberService.updateMemberContact(eq(member.getId()), any()))
                .thenThrow(new ConflictException("A member with this phone number already exists."));

        assertThatThrownBy(() -> service.approve(proposal.getId(), admin)).isInstanceOf(ConflictException.class);
        assertThat(proposal.getStatus()).isEqualTo(MemberUpdateStatus.PENDING);
        verifyNoInteractions(outboxService);
    }
}
