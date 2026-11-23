package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.CollectorLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface CollectorLocationRepo extends JpaRepository<CollectorLocation, Long> {

    /** Stores a point; a re-sent one (same collector, phone time and trigger) is skipped. Returns 1 or 0. */
    @Modifying
    @Query(value = """
            insert into collector_location (collector_id, latitude, longitude, accuracy_m, battery_percent, trigger,
                                            payment_idempotency_key, recorded_at, received_at)
            values (:collectorId, :latitude, :longitude, :accuracy, :battery, :trigger, :paymentKey, :recordedAt, :receivedAt)
            on conflict (collector_id, recorded_at, trigger) do nothing
            """, nativeQuery = true)
    int insertIfNew(@Param("collectorId") UUID collectorId, @Param("latitude") BigDecimal latitude,
                    @Param("longitude") BigDecimal longitude, @Param("accuracy") BigDecimal accuracy,
                    @Param("battery") Integer battery, @Param("trigger") String trigger,
                    @Param("paymentKey") String paymentKey, @Param("recordedAt") LocalDateTime recordedAt,
                    @Param("receivedAt") LocalDateTime receivedAt);

    /** A point arrived from this collector's phone since the given server time. */
    boolean existsByCollectorIdAndReceivedAtGreaterThanEqual(UUID collectorId, LocalDateTime since);

    /** A point was taken (phone time) in the given period. */
    boolean existsByCollectorIdAndRecordedAtBetween(UUID collectorId, LocalDateTime from, LocalDateTime to);

    /** A collector's trail for one period, in time order. */
    List<CollectorLocation> findByCollectorIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtAsc(
            UUID collectorId, LocalDateTime from, LocalDateTime to);

    @Modifying
    @Query("delete from CollectorLocation l where l.recordedAt < :before")
    int deleteRecordedBefore(@Param("before") LocalDateTime before);
}
