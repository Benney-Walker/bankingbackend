package com.bynexlabs.bankingbackend.Repository;

import com.bynexlabs.bankingbackend.models.CollectorShortageRepayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollectorShortageRepaymentRepo extends JpaRepository<CollectorShortageRepayment, UUID> {

    @Query("select coalesce(sum(r.amount), 0) from CollectorShortageRepayment r where r.collector.id = :collectorId")
    BigDecimal sumRepaid(@Param("collectorId") UUID collectorId);

    /** Collectors with repayments, and their total: [collector id, total]. */
    @Query("select r.collector.id, sum(r.amount) from CollectorShortageRepayment r group by r.collector.id")
    List<Object[]> repaidTotals();

    List<CollectorShortageRepayment> findByCollector_IdOrderByReceivedAtDesc(UUID collectorId);

    Optional<CollectorShortageRepayment> findByTransaction_Id(UUID transactionId);
}
