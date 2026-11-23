package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.location.LocationBatchRequest;
import com.bynexlabs.bankingbackend.DTO.location.LocationBatchResponse;
import com.bynexlabs.bankingbackend.DTO.location.LocationPointRequest;
import com.bynexlabs.bankingbackend.DTO.location.LocationPointResponse;
import com.bynexlabs.bankingbackend.Repository.CollectorLocationRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.CollectorRules;
import com.bynexlabs.bankingbackend.enums.LocationTrigger;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.CollectorLocation;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collector location tracking (owner decisions 2026-11-23, increment #6, D34):
 * - the app sends a point every 5 minutes and on each payment (position, accuracy, battery,
 *   phone time, trigger), including points taken offline; re-sent points are stored once;
 * - a payment is refused unless the phone sent a point in the last 15 minutes, or, for a payment
 *   recorded offline, a point was taken within 15 minutes of it;
 * - the office sees a collector's points for a day with the payments placed on them: agents for
 *   their own branch's collectors, administrators for all;
 * - points are deleted 90 days after they were taken.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorLocationService {

    static final Duration RECENT = Duration.ofMinutes(15);

    private static final Duration KEEP_FOR = Duration.ofDays(90);

    private final CollectorLocationRepo locationRepo;

    private final StaffRepo staffRepo;

    private final TransactionRepo transactionRepo;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    // --- collector side -----------------------------------------------------------------

    @Transactional
    public LocationBatchResponse record(LocationBatchRequest batch, Staff collector) {
        LocalDateTime now = LocalDateTime.now(clock);
        batch.getPoints().forEach(point -> requireValid(point, now));
        int stored = 0;
        for (LocationPointRequest point : batch.getPoints()) {
            stored += locationRepo.insertIfNew(collector.getId(), point.getLatitude(), point.getLongitude(),
                    point.getAccuracyMeters(), point.getBatteryPercent(), point.getTrigger().name(),
                    point.getTrigger() == LocationTrigger.PAYMENT ? point.getPaymentIdempotencyKey().trim() : null,
                    point.getRecordedAt(), now);
        }
        return new LocationBatchResponse(stored, batch.getPoints().size() - stored);
    }

    /**
     * Called before a new payment is saved. Online ({@code recordedAt} null): a point must have
     * arrived from the phone in the last 15 minutes. Offline: a point must have been taken within
     * 15 minutes of the payment, so the app sends its points before its payments.
     */
    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public void requireRecentLocation(Staff collector, LocalDateTime recordedAt, LocalDateTime now) {
        if (recordedAt == null) {
            if (!locationRepo.existsByCollectorIdAndReceivedAtGreaterThanEqual(collector.getId(), now.minus(RECENT))) {
                throw new ConflictException("No location from this phone in the last 15 minutes. "
                        + "Turn on location and try again");
            }
        } else if (!locationRepo.existsByCollectorIdAndRecordedAtBetween(collector.getId(),
                recordedAt.minus(RECENT), recordedAt.plus(RECENT))) {
            throw new ConflictException("No location was recorded within 15 minutes of this payment. "
                    + "Send the phone's location points first");
        }
    }

    // --- office side --------------------------------------------------------------------

    /** A collector's points for one day (Africa/Accra), in time order, with their payments. */
    @Transactional(readOnly = true)
    public List<LocationPointResponse> trail(String collectorStaffId, LocalDate date, Staff viewer,
                                             boolean viewerIsAdmin) {
        Staff collector = staffRepo.findByStaffId(collectorStaffId)
                .filter(staff -> staff.getRoles() != null
                        && staff.getRoles().stream().anyMatch(role -> role.getRole() == Roles.COLLECTOR))
                .orElseThrow(() -> new ResourceNotFoundException("Collector not found"));
        if (!viewerIsAdmin && (viewer.getBranch() == null || collector.getBranch() == null
                || !viewer.getBranch().getId().equals(collector.getBranch().getId()))) {
            throw new ForbiddenException("This collector is from another branch");
        }
        LocalDate day = date == null ? LocalDate.now(clock) : date;
        List<CollectorLocation> points = locationRepo
                .findByCollectorIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
                        collector.getId(), day.atStartOfDay(), day.plusDays(1).atStartOfDay());

        List<String> keys = points.stream().map(CollectorLocation::getPaymentIdempotencyKey)
                .filter(Objects::nonNull).distinct().toList();
        // Only this collector's own payments are placed on the trail.
        Map<String, Transaction> payments = keys.isEmpty() ? Map.of()
                : transactionRepo.findByIdempotencyKeyIn(keys).stream()
                    .filter(payment -> payment.getInitiatedBy() != null
                            && collector.getId().equals(payment.getInitiatedBy().getId()))
                    .collect(Collectors.toMap(Transaction::getIdempotencyKey, Function.identity()));

        return points.stream().map(point -> new LocationPointResponse(point.getLatitude(), point.getLongitude(),
                point.getAccuracyMeters(), point.getBatteryPercent(), point.getTrigger(), point.getRecordedAt(),
                point.getReceivedAt(), payment(payments.get(point.getPaymentIdempotencyKey())))).toList();
    }

    /** Daily housekeeping: points are kept 90 days after they were taken. */
    @Scheduled(cron = "${app.collector.location-cleanup-cron:0 45 3 * * *}", zone = "Africa/Accra")
    @Transactional
    public void deleteOldPoints() {
        int deleted = locationRepo.deleteRecordedBefore(LocalDateTime.now(clock).minus(KEEP_FOR));
        if (deleted > 0) {
            log.info("Deleted {} collector location points older than 90 days", deleted);
        }
    }

    // --- helpers ------------------------------------------------------------------------

    private static void requireValid(LocationPointRequest point, LocalDateTime now) {
        CollectorRules.requireValidRecordedAt(point.getRecordedAt(), now);
        boolean hasKey = point.getPaymentIdempotencyKey() != null && !point.getPaymentIdempotencyKey().isBlank();
        if (point.getTrigger() == LocationTrigger.PAYMENT && !hasKey) {
            throw new BadRequestException("A PAYMENT point needs the payment's paymentIdempotencyKey");
        }
        if (point.getTrigger() == LocationTrigger.EVERY_5_MIN && hasKey) {
            throw new BadRequestException("Only a PAYMENT point carries a paymentIdempotencyKey");
        }
    }

    private static LocationPointResponse.Payment payment(Transaction payment) {
        if (payment == null) {
            return null;
        }
        Member member = payment.getMemberAccount() == null ? null : payment.getMemberAccount().getMember();
        return new LocationPointResponse.Payment(payment.getId(), payment.getTransactionType(), payment.getAmount(),
                payment.getStatus(),
                member == null ? null : member.getFirstName() + " " + member.getLastName(),
                payment.getMemberAccount() == null ? null : payment.getMemberAccount().getAccountNumber());
    }
}
