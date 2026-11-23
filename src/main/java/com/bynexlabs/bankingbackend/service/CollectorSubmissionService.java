package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentResponse;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorSubmissionResponse;
import com.bynexlabs.bankingbackend.Repository.CollectorSubmissionRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.CollectorRules;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
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
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.CollectorSubmission;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Collector daily cash submission (owner decisions 2026-11-15):
 * 1. The collector taps submit to close the day. Allowed once none of their payments is still in
 *    its 10-minute window. It covers every payment not yet in a submission: COMPLETED ones make up
 *    the expected cash, FAILED ones are counted separately. From then on they can't record.
 * 2. The agent of the collector's branch, or an administrator, counts the cash and approves with
 *    the amount counted. The counted cash moves from 1015 Cash with Collectors to 1010 Cash on
 *    Hand; a surplus goes to 2030 Cash Overages (Suspense). No shortage is allowed (owner
 *    decision 2026-11-23): a count below the expected cash is refused and the day stays waiting.
 * 3. Approval signs the collector out everywhere, and they can't sign in again until the next
 *    day (Africa/Accra).
 */
@Service
@RequiredArgsConstructor
public class CollectorSubmissionService {

    private static final int MAX_PAGE_SIZE = 100;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CollectorSubmissionRepo submissionRepo;

    private final TransactionRepo transactionRepo;

    private final TransactionService transactionService;

    private final StaffRepo staffRepo;

    private final PostingService postingService;

    private final SessionService sessionService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    // --- collector side -----------------------------------------------------------------

    /** Closes the collector's day. Submitting again while waiting returns the same submission. */
    @Transactional
    public CollectorSubmissionResponse submit(Staff collector) {
        Staff locked = lockCollector(collector.getId());
        Optional<CollectorSubmission> open = submissionRepo.findByCollector_IdAndStatus(locked.getId(),
                CollectorSubmissionStatus.SUBMITTED);
        if (open.isPresent()) {
            return response(open.get(), false);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (closedOn(locked.getId(), now.toLocalDate())) {
            throw new ConflictException("Your collections for today have already been approved");
        }

        List<Transaction> pending = transactionRepo.findPendingCollectorPayments(locked.getId());
        if (!pending.isEmpty()) {
            LocalDateTime lastWindowCloses = pending.stream()
                    .map(payment -> CollectorRules.correctableUntil(payment.getCreatedAt(), payment.getRecordedAt()))
                    .filter(Objects::nonNull)
                    .max(Comparator.naturalOrder())
                    .orElse(now);
            throw new ConflictException(pending.size() + " payment(s) are still in their 10-minute correction window. "
                    + (lastWindowCloses.isAfter(now)
                        ? "You can submit after " + lastWindowCloses.format(TIME)
                        : "They're being finalised; try again in a minute"));
        }

        List<Transaction> payments = transactionRepo.findUnsubmittedCollectorPayments(locked.getId());
        int completed = 0;
        int failed = 0;
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal failedAmount = BigDecimal.ZERO;
        for (Transaction payment : payments) {
            if (payment.getStatus() == TransactionStatus.COMPLETED) {
                completed++;
                expected = expected.add(payment.getAmount());
            } else {
                failed++;
                failedAmount = failedAmount.add(payment.getAmount());
            }
        }

        CollectorSubmission submission = submissionRepo.save(CollectorSubmission.builder()
                .id(UUID.randomUUID())
                .collector(locked)
                .branch(locked.getBranch())
                .status(CollectorSubmissionStatus.SUBMITTED)
                .submittedAt(now)
                .paymentCount(completed)
                .expectedAmount(expected)
                .failedCount(failed)
                .failedAmount(failedAmount)
                .build());
        payments.forEach(payment -> payment.setCollectorSubmission(submission));
        return response(submission, false);
    }

    /** The collector's own submissions, newest first. */
    @Transactional(readOnly = true)
    public PagedModel<CollectorSubmissionResponse> ownSubmissions(Staff collector, int page, int size) {
        return new PagedModel<>(submissionRepo.findByCollector_Id(collector.getId(), pageRequest(page, size))
                .map(submission -> response(submission, false)));
    }

    /**
     * Called before a payment is recorded. Locks the collector's row so a payment and a submission
     * can't happen at the same moment, then refuses once the day is closed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCanRecord(Staff collector) {
        Staff locked = lockCollector(collector.getId());
        if (submissionRepo.existsByCollector_IdAndStatus(locked.getId(), CollectorSubmissionStatus.SUBMITTED)) {
            throw new ConflictException("You've submitted your collections for the day. No more payments can be recorded until they're approved");
        }
        if (closedOn(locked.getId(), LocalDate.now(clock))) {
            throw new ConflictException("Your collections for today have been approved. You can record again tomorrow");
        }
    }

    /** True once the collector's submission made today has been approved: they stay signed out until tomorrow. */
    @Transactional(readOnly = true)
    public boolean isClosedForToday(UUID collectorId) {
        return closedOn(collectorId, LocalDate.now(clock));
    }

    // --- office side --------------------------------------------------------------------

    /** Administrators see every branch; agents only their own branch's collectors. Default: waiting ones. */
    @Transactional(readOnly = true)
    public PagedModel<CollectorSubmissionResponse> list(CollectorSubmissionStatus status, int page, int size,
                                                        Staff reviewer, boolean reviewerIsAdmin) {
        CollectorSubmissionStatus wanted = status == null ? CollectorSubmissionStatus.SUBMITTED : status;
        PageRequest pageRequest = pageRequest(page, size);
        if (reviewerIsAdmin) {
            return new PagedModel<>(submissionRepo.findByStatus(wanted, pageRequest).map(s -> response(s, false)));
        }
        if (reviewer.getBranch() == null) {
            throw new ForbiddenException("You are not assigned to a branch");
        }
        return new PagedModel<>(submissionRepo.findByStatusAndBranch_Id(wanted, reviewer.getBranch().getId(), pageRequest)
                .map(s -> response(s, false)));
    }

    /** One submission with its payments. */
    @Transactional(readOnly = true)
    public CollectorSubmissionResponse get(UUID submissionId, Staff reviewer, boolean reviewerIsAdmin) {
        CollectorSubmission submission = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
        requireReviewerBranch(submission, reviewer, reviewerIsAdmin);
        return response(submission, true);
    }

    /**
     * Approves with the cash counted. Repeating the same approval returns it unchanged; a
     * different amount after approval is refused, since an approved submission is final.
     */
    @Transactional
    public CollectorSubmissionResponse approve(UUID submissionId, BigDecimal countedAmount, Staff approver,
                                               boolean approverIsAdmin) {
        BigDecimal counted = requireValidCounted(countedAmount);
        CollectorSubmission submission = submissionRepo.lockById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission not found"));
        requireReviewerBranch(submission, approver, approverIsAdmin);
        if (submission.getStatus() == CollectorSubmissionStatus.APPROVED) {
            if (submission.getCountedAmount().compareTo(counted) == 0) {
                return response(submission, true);
            }
            throw new ConflictException("This submission was already approved with GHS "
                    + MoneyMovementService.ghs(submission.getCountedAmount()) + " counted");
        }

        // Owner decision 2026-11-23: no shortage is allowed. The collector must hand in at least
        // the expected cash before the day can be approved; the submission stays waiting.
        BigDecimal expected = submission.getExpectedAmount();
        if (counted.compareTo(expected) < 0) {
            throw new ConflictException("The cash counted is GHS " + MoneyMovementService.ghs(expected.subtract(counted))
                    + " short of the expected GHS " + MoneyMovementService.ghs(expected)
                    + ". The collector must hand in the full amount before this can be approved");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal overage = counted.subtract(expected);
        Staff approverRef = staffRepo.getReferenceById(approver.getId());

        // Nothing to post on a day with nothing expected and nothing handed in.
        if (counted.signum() > 0) {
            Transaction handover = transactionService.record(Transaction.builder()
                    .idempotencyKey("collector-handover-" + submission.getId())
                    .transactionType(TransactionType.COLLECTOR_HANDOVER)
                    .amount(counted)
                    .initiatedBy(approverRef)
                    .description("Cash handed in by collector " + submission.getCollector().getStaffId())
                    .status(TransactionStatus.COMPLETED)
                    .channel(TransactionChannel.OFFICE)
                    .createdAt(now)
                    .postedAt(now)
                    .build());
            postingService.post(handover, PostingService.Posting.of(PostingEvent.COLLECTOR_HANDOVER, null, null)
                    .with(AmountPart.PRINCIPAL, counted)
                    .with(AmountPart.SETTLED, counted.subtract(overage))
                    .with(AmountPart.OVERAGE, overage), now);
            submission.setHandoverTransaction(handover);
        }

        submission.setStatus(CollectorSubmissionStatus.APPROVED);
        submission.setCountedAmount(counted);
        submission.setShortageAmount(BigDecimal.ZERO);
        submission.setOverageAmount(overage);
        submission.setApprovedBy(approverRef);
        submission.setApprovedAt(now);

        // Owner decision 2026-11-15: approval signs the collector out on every device.
        sessionService.endAllSessions(submission.getCollector().getId(), SessionEndReason.DAILY_APPROVAL);
        return response(submission, true);
    }

    // --- helpers ------------------------------------------------------------------------

    private Staff lockCollector(UUID collectorId) {
        return staffRepo.lockById(collectorId)
                .orElseThrow(() -> new ResourceNotFoundException("Collector not found"));
    }

    private boolean closedOn(UUID collectorId, LocalDate day) {
        return submissionRepo.existsByCollector_IdAndStatusAndSubmittedAtGreaterThanEqualAndSubmittedAtLessThan(
                collectorId, CollectorSubmissionStatus.APPROVED, day.atStartOfDay(), day.plusDays(1).atStartOfDay());
    }

    // Agents approve only their own branch's collectors (desktop spec 5.3); administrators any branch.
    private static void requireReviewerBranch(CollectorSubmission submission, Staff reviewer, boolean reviewerIsAdmin) {
        if (reviewerIsAdmin) {
            return;
        }
        if (reviewer.getBranch() == null || !reviewer.getBranch().getId().equals(submission.getBranch().getId())) {
            throw new ForbiddenException("This submission is from another branch");
        }
    }

    private static BigDecimal requireValidCounted(BigDecimal counted) {
        if (counted == null) {
            throw new BadRequestException("countedAmount is required");
        }
        if (counted.signum() < 0) {
            throw new BadRequestException("countedAmount can't be negative");
        }
        return counted.signum() == 0 ? BigDecimal.ZERO : MoneyRules.requireValidAmount(counted);
    }

    private CollectorSubmissionResponse response(CollectorSubmission submission, boolean withPayments) {
        List<CollectorPaymentResponse> payments = withPayments
                ? transactionRepo.findByCollectorSubmission_IdOrderByCreatedAtAsc(submission.getId()).stream()
                    .map(payment -> CollectorPaymentResponse.from(payment, null)).toList()
                : null;
        return CollectorSubmissionResponse.from(submission, payments);
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
    }
}
