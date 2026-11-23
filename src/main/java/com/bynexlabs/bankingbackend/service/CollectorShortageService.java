package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorShortageResponse;
import com.bynexlabs.bankingbackend.DTO.collector.ShortageRepaymentRequest;
import com.bynexlabs.bankingbackend.Repository.CollectorShortageRepaymentRepo;
import com.bynexlabs.bankingbackend.Repository.CollectorSubmissionRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.CollectorShortageRepayment;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Collector shortage repayment (owner decisions 2026-11-23, increment #8):
 * - a shortage recorded on an approved submission stays in 1015 Cash with Collectors;
 * - the collector pays it back at their branch (the agent records it) or at the main office (an
 *   administrator records it); each repayment posts 1010 debit, 1015 credit;
 * - a repayment can't be more than what's owed;
 * - an unpaid shortage doesn't stop the collector working; no SMS either way.
 */
@Service
@RequiredArgsConstructor
public class CollectorShortageService {

    private final CollectorSubmissionRepo submissionRepo;

    private final CollectorShortageRepaymentRepo repaymentRepo;

    private final StaffRepo staffRepo;

    private final TransactionRepo transactionRepo;

    private final TransactionService transactionService;

    private final PostingService postingService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** What one collector owes, with their repayments, newest first. */
    @Transactional(readOnly = true)
    public CollectorShortageResponse summary(String collectorStaffId, Staff viewer, boolean viewerIsAdmin) {
        Staff collector = visibleCollector(collectorStaffId, viewer, viewerIsAdmin);
        return summary(collector, true);
    }

    /** Collectors who still owe something, largest first. Agents: own branch; administrators: all. */
    @Transactional(readOnly = true)
    public List<CollectorShortageResponse> owing(Staff viewer, boolean viewerIsAdmin) {
        if (!viewerIsAdmin && viewer.getBranch() == null) {
            throw new ForbiddenException("You are not assigned to a branch");
        }
        Map<UUID, BigDecimal> repaid = new HashMap<>();
        for (Object[] row : repaymentRepo.repaidTotals()) {
            repaid.put((UUID) row[0], (BigDecimal) row[1]);
        }
        return submissionRepo.shortageTotals().stream()
                .filter(row -> ((BigDecimal) row[1]).subtract(repaid.getOrDefault((UUID) row[0], BigDecimal.ZERO)).signum() > 0)
                .map(row -> staffRepo.findById((UUID) row[0]).orElse(null))
                .filter(collector -> collector != null && (viewerIsAdmin || sameBranch(viewer, collector)))
                .map(collector -> summary(collector, false))
                .sorted(Comparator.comparing(CollectorShortageResponse::outstanding).reversed())
                .toList();
    }

    /** Records cash paid back. A retry with the same key returns the same result. */
    @Transactional
    public CollectorShortageResponse repay(String collectorStaffId, ShortageRepaymentRequest request, Staff receivedBy,
                                           boolean receivedByAdmin) {
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());
        Staff collector = visibleCollector(collectorStaffId, receivedBy, receivedByAdmin);

        transactionRepo.lockIdempotencyKey(key);
        Optional<Transaction> existing = transactionRepo.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Optional<CollectorShortageRepayment> earlier = repaymentRepo.findByTransaction_Id(existing.get().getId());
            if (earlier.isPresent() && earlier.get().getCollector().getId().equals(collector.getId())
                    && earlier.get().getAmount().compareTo(amount) == 0) {
                return summary(collector, true);
            }
            throw new ConflictException("This idempotency key was already used for a different transaction");
        }

        // Locked, so two repayments for the same collector can't both pass the check below.
        staffRepo.lockById(collector.getId());
        BigDecimal outstanding = outstanding(collector.getId());
        if (amount.compareTo(outstanding) > 0) {
            throw new ConflictException(outstanding.signum() == 0 ? "This collector owes nothing"
                    : "That's more than this collector owes (GHS " + MoneyMovementService.ghs(outstanding) + ")");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        Staff receiver = staffRepo.getReferenceById(receivedBy.getId());
        Transaction transaction = transactionService.record(Transaction.builder()
                .idempotencyKey(key)
                .transactionType(TransactionType.COLLECTOR_SHORTAGE_REPAYMENT)
                .amount(amount)
                .initiatedBy(receiver)
                .description("Shortage repaid by collector " + collector.getStaffId())
                .status(TransactionStatus.COMPLETED)
                .channel(TransactionChannel.OFFICE)
                .createdAt(now)
                .postedAt(now)
                .build());
        postingService.post(transaction, PostingService.Posting.of(PostingEvent.COLLECTOR_SHORTAGE_REPAYMENT, null, null)
                .with(AmountPart.PRINCIPAL, amount), now);
        repaymentRepo.save(CollectorShortageRepayment.builder()
                .id(UUID.randomUUID())
                .collector(collector)
                .amount(amount)
                .transaction(transaction)
                .receivedBy(receiver)
                .receivedAt(now)
                .note(request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim())
                .build());
        return summary(collector, true);
    }

    private Staff visibleCollector(String collectorStaffId, Staff viewer, boolean viewerIsAdmin) {
        Staff collector = staffRepo.findByStaffId(collectorStaffId)
                .filter(staff -> staff.getRoles() != null
                        && staff.getRoles().stream().anyMatch(role -> role.getRole() == Roles.COLLECTOR))
                .orElseThrow(() -> new ResourceNotFoundException("Collector not found"));
        if (!viewerIsAdmin && !sameBranch(viewer, collector)) {
            throw new ForbiddenException("This collector is from another branch");
        }
        return collector;
    }

    private static boolean sameBranch(Staff viewer, Staff collector) {
        return viewer.getBranch() != null && collector.getBranch() != null
                && viewer.getBranch().getId().equals(collector.getBranch().getId());
    }

    private BigDecimal outstanding(UUID collectorId) {
        return submissionRepo.sumShortage(collectorId).subtract(repaymentRepo.sumRepaid(collectorId));
    }

    private CollectorShortageResponse summary(Staff collector, boolean withRepayments) {
        BigDecimal shortage = submissionRepo.sumShortage(collector.getId());
        BigDecimal repaid = repaymentRepo.sumRepaid(collector.getId());
        List<CollectorShortageResponse.Repayment> repayments = withRepayments
                ? repaymentRepo.findByCollector_IdOrderByReceivedAtDesc(collector.getId()).stream()
                    .map(r -> new CollectorShortageResponse.Repayment(r.getTransaction().getId(), r.getAmount(),
                            r.getReceivedBy().getStaffId(), r.getReceivedAt(), r.getNote()))
                    .toList()
                : null;
        return new CollectorShortageResponse(collector.getStaffId(),
                collector.getFirstName() + " " + collector.getLastName(),
                collector.getBranch() == null ? null : collector.getBranch().getName(),
                shortage, repaid, shortage.subtract(repaid), repayments);
    }
}
