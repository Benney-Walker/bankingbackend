package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorShortageResponse;
import com.bynexlabs.bankingbackend.DTO.collector.ShortageRepaymentRequest;
import com.bynexlabs.bankingbackend.Repository.CollectorShortageRepaymentRepo;
import com.bynexlabs.bankingbackend.Repository.CollectorSubmissionRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.CollectorShortageRepayment;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import com.bynexlabs.bankingbackend.models.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class CollectorShortageServiceTest {

    private static final ZoneId ACCRA = ZoneId.of("Africa/Accra");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 19, 0);

    @Mock
    private CollectorSubmissionRepo submissionRepo;
    @Mock
    private CollectorShortageRepaymentRepo repaymentRepo;
    @Mock
    private StaffRepo staffRepo;
    @Mock
    private TransactionRepo transactionRepo;
    @Mock
    private TransactionService transactionService;
    @Mock
    private PostingService postingService;

    private CollectorShortageService service;

    private final Branch tarkwa = Branch.builder().id(UUID.randomUUID()).name("Tarkwa").build();
    private final Branch takoradi = Branch.builder().id(UUID.randomUUID()).name("Takoradi").build();
    private final Staff collector = collector();
    private final Staff agent = Staff.builder().id(UUID.randomUUID()).staffId("900105").branch(tarkwa).build();
    private final Staff otherAgent = Staff.builder().id(UUID.randomUUID()).staffId("900106").branch(takoradi).build();

    @BeforeEach
    void setUp() {
        service = new CollectorShortageService(submissionRepo, repaymentRepo, staffRepo, transactionRepo,
                transactionService, postingService);
        service.clock = Clock.fixed(NOW.atZone(ACCRA).toInstant(), ACCRA);
        lenient().when(staffRepo.findByStaffId("900110")).thenReturn(Optional.of(collector));
        lenient().when(staffRepo.getReferenceById(any())).thenAnswer(invocation ->
                invocation.getArgument(0).equals(agent.getId()) ? agent : otherAgent);
        lenient().when(transactionService.record(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Staff collector() {
        Staff staff = Staff.builder().id(UUID.randomUUID()).staffId("900110").firstName("Kofi").lastName("Asare")
                .branch(tarkwa).build();
        StaffRoles row = new StaffRoles();
        row.setStaff(staff);
        row.setRole(Roles.COLLECTOR);
        staff.setRoles(List.of(row));
        return staff;
    }

    private void owes(String shortage, String repaid) {
        lenient().when(submissionRepo.sumShortage(collector.getId())).thenReturn(new BigDecimal(shortage));
        lenient().when(repaymentRepo.sumRepaid(collector.getId())).thenReturn(new BigDecimal(repaid));
    }

    private static ShortageRepaymentRequest repayment(String amount) {
        return ShortageRepaymentRequest.builder().amount(new BigDecimal(amount)).idempotencyKey("key-1").note("Paid at office").build();
    }

    @Test
    void summaryShowsWhatsOwed() {
        owes("50", "20");

        CollectorShortageResponse response = service.summary("900110", agent, false);

        assertThat(response.totalShortage()).isEqualByComparingTo("50");
        assertThat(response.totalRepaid()).isEqualByComparingTo("20");
        assertThat(response.outstanding()).isEqualByComparingTo("30");
        assertThat(response.branchName()).isEqualTo("Tarkwa");
    }

    @Test
    void branchAgentRecordsARepaymentThatMovesCashTo1010() {
        owes("50", "0");

        service.repay("900110", repayment("50"), agent, false);

        ArgumentCaptor<Transaction> transaction = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionService).record(transaction.capture());
        assertThat(transaction.getValue().getTransactionType()).isEqualTo(TransactionType.COLLECTOR_SHORTAGE_REPAYMENT);
        assertThat(transaction.getValue().getChannel()).isEqualTo(TransactionChannel.OFFICE);
        assertThat(transaction.getValue().getMemberAccount()).isNull();
        ArgumentCaptor<PostingService.Posting> posting = ArgumentCaptor.forClass(PostingService.Posting.class);
        verify(postingService).post(any(Transaction.class), posting.capture(), eq(NOW));
        assertThat(posting.getValue().event()).isEqualTo(PostingEvent.COLLECTOR_SHORTAGE_REPAYMENT);
        assertThat(posting.getValue().amounts().get(AmountPart.PRINCIPAL)).isEqualByComparingTo("50");
        ArgumentCaptor<CollectorShortageRepayment> saved = ArgumentCaptor.forClass(CollectorShortageRepayment.class);
        verify(repaymentRepo).save(saved.capture());
        assertThat(saved.getValue().getNote()).isEqualTo("Paid at office");
        verify(staffRepo).lockById(collector.getId());
    }

    @Test
    void partPaymentIsAllowed() {
        owes("50", "0");

        service.repay("900110", repayment("20"), agent, false);

        verify(repaymentRepo).save(any());
    }

    @Test
    void moreThanWhatsOwedIsRefused() {
        owes("50", "40");

        assertThatThrownBy(() -> service.repay("900110", repayment("20"), agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("That's more than this collector owes (GHS 10.00)");
        verifyNoInteractions(transactionService, postingService);
    }

    @Test
    void nothingOwedIsRefused() {
        owes("0", "0");

        assertThatThrownBy(() -> service.repay("900110", repayment("5"), agent, false))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This collector owes nothing");
    }

    @Test
    void agentOfAnotherBranchCantRecord() {
        assertThatThrownBy(() -> service.repay("900110", repayment("5"), otherAgent, false))
                .isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(transactionService);
    }

    @Test
    void administratorRecordsForAnyBranch() {
        owes("50", "0");

        service.repay("900110", repayment("50"), otherAgent, true);

        verify(repaymentRepo).save(any());
    }

    @Test
    void retryWithTheSameKeyReturnsTheSameRepayment() {
        owes("50", "50");
        Transaction earlier = Transaction.builder().id(UUID.randomUUID()).idempotencyKey("key-1").build();
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(earlier));
        when(repaymentRepo.findByTransaction_Id(earlier.getId())).thenReturn(Optional.of(CollectorShortageRepayment.builder()
                .collector(collector).amount(new BigDecimal("50")).build()));

        assertThat(service.repay("900110", repayment("50"), agent, false).outstanding()).isEqualByComparingTo("0");
        verify(transactionService, never()).record(any());
    }

    @Test
    void keyUsedForSomethingElseIsAConflict() {
        Transaction earlier = Transaction.builder().id(UUID.randomUUID()).idempotencyKey("key-1").build();
        when(transactionRepo.findByIdempotencyKey("key-1")).thenReturn(Optional.of(earlier));

        assertThatThrownBy(() -> service.repay("900110", repayment("50"), agent, false))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void owingListShowsOnlyCollectorsStillOwingInTheAgentsBranch() {
        Staff paidUp = Staff.builder().id(UUID.randomUUID()).staffId("900111").firstName("Ama").lastName("B").branch(tarkwa).build();
        when(submissionRepo.shortageTotals()).thenReturn(List.of(
                new Object[]{collector.getId(), new BigDecimal("50")},
                new Object[]{paidUp.getId(), new BigDecimal("10")}));
        when(repaymentRepo.repaidTotals()).thenReturn(List.<Object[]>of(new Object[]{paidUp.getId(), new BigDecimal("10")}));
        when(staffRepo.findById(collector.getId())).thenReturn(Optional.of(collector));
        owes("50", "0");

        List<CollectorShortageResponse> owing = service.owing(agent, false);

        assertThat(owing).extracting(CollectorShortageResponse::collectorStaffId).containsExactly("900110");
        assertThat(service.owing(otherAgent, false)).isEmpty();
    }
}
