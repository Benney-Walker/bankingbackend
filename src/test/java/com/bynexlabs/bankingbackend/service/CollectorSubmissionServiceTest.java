package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorSubmissionResponse;
import com.bynexlabs.bankingbackend.Repository.CollectorSubmissionRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.SessionEndReason;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.CollectorSubmission;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
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
class CollectorSubmissionServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 17, 0);

    @Mock
    private CollectorSubmissionRepo submissionRepo;
    @Mock
    private TransactionRepo transactionRepo;
    @Mock
    private TransactionService transactionService;
    @Mock
    private StaffRepo staffRepo;
    @Mock
    private PostingService postingService;
    @Mock
    private SessionService sessionService;

    private CollectorSubmissionService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch takoradi = Branch.builder().id(UUID.randomUUID()).name("Takoradi").build();
    private final Staff collector = Staff.builder().id(UUID.randomUUID()).staffId("900110").firstName("Kofi")
            .lastName("Asare").branch(tarkwa).build();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900105").branch(tarkwa).build();
    private final Staff otherAgent = Staff.builder().id(UUID.randomUUID()).staffId("900106").branch(takoradi).build();
    private final Staff administrator = Staff.builder().id(UUID.randomUUID()).staffId("900101").branch(takoradi).build();

    @BeforeEach
    void setUp() {
        service = new CollectorSubmissionService(submissionRepo, transactionRepo, transactionService, staffRepo,
                postingService, sessionService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        lenient().when(staffRepo.lockById(collector.getId())).thenReturn(Optional.of(collector));
        lenient().when(submissionRepo.save(any(CollectorSubmission.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(transactionService.record(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Transaction payment(String amount, TransactionStatus status) {
        return Transaction.builder().id(UUID.randomUUID()).idempotencyKey(UUID.randomUUID().toString())
                .transactionType(TransactionType.DEPOSIT).amount(new BigDecimal(amount)).status(status)
                .channel(TransactionChannel.COLLECTOR).createdAt(NOW.minusHours(2)).build();
    }

    private CollectorSubmission submitted(String expected) {
        CollectorSubmission submission = CollectorSubmission.builder().id(UUID.randomUUID()).collector(collector)
                .branch(tarkwa).status(CollectorSubmissionStatus.SUBMITTED).submittedAt(NOW.minusMinutes(30))
                .paymentCount(2).expectedAmount(new BigDecimal(expected)).failedCount(0).failedAmount(BigDecimal.ZERO)
                .build();
        lenient().when(submissionRepo.lockById(submission.getId())).thenReturn(Optional.of(submission));
        return submission;
    }

    private void approverIs(Staff approver) {
        lenient().when(staffRepo.getReferenceById(approver.getId())).thenReturn(approver);
    }

    private PostingService.Posting posted() {
        ArgumentCaptor<PostingService.Posting> posting = ArgumentCaptor.forClass(PostingService.Posting.class);
        verify(postingService).post(any(Transaction.class), posting.capture(), eq(NOW));
        return posting.getValue();
    }

    // --- submitting -----------------------------------------------------------------------

    @Test
    void submitCoversEveryUnsubmittedPaymentAndExpectsOnlyCompletedOnes() {
        Transaction first = payment("20", TransactionStatus.COMPLETED);
        Transaction second = payment("30.50", TransactionStatus.COMPLETED);
        Transaction failed = payment("15", TransactionStatus.FAILED);
        when(transactionRepo.findUnsubmittedCollectorPayments(collector.getId())).thenReturn(List.of(first, second, failed));

        CollectorSubmissionResponse response = service.submit(collector);

        assertThat(response.status()).isEqualTo(CollectorSubmissionStatus.SUBMITTED);
        assertThat(response.paymentCount()).isEqualTo(2);
        assertThat(response.expectedAmount()).isEqualByComparingTo("50.50");
        assertThat(response.failedCount()).isEqualTo(1);
        assertThat(response.failedAmount()).isEqualByComparingTo("15");
        assertThat(response.branchName()).isEqualTo("Tarkwa");
        assertThat(first.getCollectorSubmission()).isNotNull();
        assertThat(failed.getCollectorSubmission()).isSameAs(first.getCollectorSubmission());
    }

    @Test
    void submittingAgainWhileWaitingReturnsTheSameSubmission() {
        CollectorSubmission open = submitted("40");
        when(submissionRepo.findByCollector_IdAndStatus(collector.getId(), CollectorSubmissionStatus.SUBMITTED))
                .thenReturn(Optional.of(open));

        assertThat(service.submit(collector).submissionId()).isEqualTo(open.getId());
        verify(submissionRepo, never()).save(any());
    }

    @Test
    void cantSubmitWhileAPaymentIsInItsWindow() {
        Transaction pending = payment("20", TransactionStatus.PENDING);
        pending.setCreatedAt(NOW.minusMinutes(4));
        when(transactionRepo.findPendingCollectorPayments(collector.getId())).thenReturn(List.of(pending));

        assertThatThrownBy(() -> service.submit(collector))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("You can submit after 17:06");
        verify(submissionRepo, never()).save(any());
    }

    @Test
    void cantSubmitAgainOnceTodayIsApproved() {
        when(submissionRepo.existsByCollector_IdAndStatusAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                collector.getId(), CollectorSubmissionStatus.APPROVED, NOW.toLocalDate().atStartOfDay(),
                NOW.toLocalDate().plusDays(1).atStartOfDay())).thenReturn(true);

        assertThatThrownBy(() -> service.submit(collector)).isInstanceOf(ConflictException.class);
    }

    @Test
    void aDayWithNoPaymentsCanStillBeClosed() {
        CollectorSubmissionResponse response = service.submit(collector);

        assertThat(response.paymentCount()).isZero();
        assertThat(response.expectedAmount()).isEqualByComparingTo("0");
    }

    @Test
    void recordingIsRefusedAfterSubmitting() {
        when(submissionRepo.existsByCollector_IdAndStatus(collector.getId(), CollectorSubmissionStatus.SUBMITTED))
                .thenReturn(true);

        assertThatThrownBy(() -> service.requireCanRecord(collector))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("submitted your collections");
    }

    @Test
    void recordingIsAllowedWhileTheDayIsOpen() {
        service.requireCanRecord(collector);

        verify(staffRepo).lockById(collector.getId());
    }

    // --- approving ------------------------------------------------------------------------

    @Test
    void exactCountMovesTheCashAndSignsTheCollectorOut() {
        CollectorSubmission submission = submitted("50");
        approverIs(agent);

        CollectorSubmissionResponse response = service.approve(submission.getId(), new BigDecimal("50"), agent, false);

        assertThat(response.status()).isEqualTo(CollectorSubmissionStatus.APPROVED);
        assertThat(response.overageAmount()).isEqualByComparingTo("0");
        assertThat(response.approvedByStaffId()).isEqualTo("900105");
        PostingService.Posting posting = posted();
        assertThat(posting.event()).isEqualTo(PostingEvent.COLLECTOR_HANDOVER);
        assertThat(posting.amounts().get(AmountPart.PRINCIPAL)).isEqualByComparingTo("50");
        assertThat(posting.amounts().get(AmountPart.SETTLED)).isEqualByComparingTo("50");
        assertThat(posting.amounts().get(AmountPart.OVERAGE)).isEqualByComparingTo("0");
        ArgumentCaptor<Transaction> handover = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(handover.capture());
        assertThat(handover.getValue().getTransactionType()).isEqualTo(TransactionType.COLLECTOR_HANDOVER);
        assertThat(handover.getValue().getIdempotencyKey()).isEqualTo("collector-handover-" + submission.getId());
        assertThat(handover.getValue().getMemberAccount()).isNull();
        verify(sessionService).endAllSessions(collector.getId(), SessionEndReason.DAILY_APPROVAL);
    }

    // Owner decision 2026-11-23: no shortage is allowed; the day stays waiting until the full cash is in.
    @Test
    void countBelowTheExpectedCashIsRefused() {
        CollectorSubmission submission = submitted("50");

        assertThatThrownBy(() -> service.approve(submission.getId(), new BigDecimal("45"), agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The cash counted is GHS 5.00 short of the expected GHS 50.00. "
                        + "The collector must hand in the full amount before this can be approved");
        assertThat(submission.getStatus()).isEqualTo(CollectorSubmissionStatus.SUBMITTED);
        verifyNoInteractions(postingService, transactionService, sessionService);
    }

    @Test
    void surplusGoesToSuspense() {
        CollectorSubmission submission = submitted("50");
        approverIs(agent);

        CollectorSubmissionResponse response = service.approve(submission.getId(), new BigDecimal("52.25"), agent, false);

        assertThat(response.overageAmount()).isEqualByComparingTo("2.25");
        PostingService.Posting posting = posted();
        assertThat(posting.amounts().get(AmountPart.PRINCIPAL)).isEqualByComparingTo("52.25");
        assertThat(posting.amounts().get(AmountPart.SETTLED)).isEqualByComparingTo("50");
        assertThat(posting.amounts().get(AmountPart.OVERAGE)).isEqualByComparingTo("2.25");
    }

    @Test
    void nothingCountedIsRefusedWhenCashIsExpected() {
        CollectorSubmission submission = submitted("50");

        assertThatThrownBy(() -> service.approve(submission.getId(), BigDecimal.ZERO, agent, false))
                .isInstanceOf(ConflictException.class);
        verifyNoInteractions(postingService, sessionService);
    }

    // A day with no payments: nothing expected, nothing counted, nothing posted.
    @Test
    void emptyDayApprovesWithNothingPosted() {
        CollectorSubmission submission = submitted("0");
        approverIs(agent);

        CollectorSubmissionResponse response = service.approve(submission.getId(), BigDecimal.ZERO, agent, false);

        assertThat(response.status()).isEqualTo(CollectorSubmissionStatus.APPROVED);
        verifyNoInteractions(postingService, transactionService);
        verify(sessionService).endAllSessions(collector.getId(), SessionEndReason.DAILY_APPROVAL);
    }

    @Test
    void agentCantApproveAnotherBranchsCollector() {
        CollectorSubmission submission = submitted("50");

        assertThatThrownBy(() -> service.approve(submission.getId(), new BigDecimal("50"), otherAgent, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("This submission is from another branch");
        assertThat(submission.getStatus()).isEqualTo(CollectorSubmissionStatus.SUBMITTED);
        verifyNoInteractions(postingService, sessionService);
    }

    @Test
    void administratorCanApproveAnyBranch() {
        CollectorSubmission submission = submitted("50");
        approverIs(administrator);

        assertThat(service.approve(submission.getId(), new BigDecimal("50"), administrator, true).status())
                .isEqualTo(CollectorSubmissionStatus.APPROVED);
    }

    @Test
    void repeatingTheSameApprovalChangesNothing() {
        CollectorSubmission submission = submitted("50");
        approverIs(agent);
        service.approve(submission.getId(), new BigDecimal("50"), agent, false);

        service.approve(submission.getId(), new BigDecimal("50.00"), agent, false);

        verify(postingService).post(any(), any(), any());
        verify(sessionService).endAllSessions(any(), any());
    }

    @Test
    void aDifferentAmountAfterApprovalIsRefused() {
        CollectorSubmission submission = submitted("50");
        approverIs(agent);
        service.approve(submission.getId(), new BigDecimal("50"), agent, false);

        assertThatThrownBy(() -> service.approve(submission.getId(), new BigDecimal("40"), agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already approved with GHS 50.00");
    }

    @Test
    void negativeCountIsRejected() {
        assertThatThrownBy(() -> service.approve(UUID.randomUUID(), new BigDecimal("-1"), agent, false))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(submissionRepo);
    }

    // --- listing --------------------------------------------------------------------------

    @Test
    void agentListsOnlyTheirBranch() {
        when(submissionRepo.findByStatusAndBranch_Id(eq(CollectorSubmissionStatus.SUBMITTED), eq(tarkwa.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, 0, 20, agent, false);

        verify(submissionRepo, never()).findByStatus(any(), any());
    }

    @Test
    void administratorListsEveryBranch() {
        when(submissionRepo.findByStatus(eq(CollectorSubmissionStatus.APPROVED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(CollectorSubmissionStatus.APPROVED, 0, 20, administrator, true);

        verify(submissionRepo, never()).findByStatusAndBranch_Id(any(), any(), any());
    }
}
