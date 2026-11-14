package com.bynexlabs.bankingbackend.DTO.collector;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * A collector's payments for one day and their totals. Totals leave out FAILED payments; PENDING
 * ones are included because the cash has been collected.
 */
public record CollectorDaySummary(LocalDate date, int count, BigDecimal savingsTotal, BigDecimal sharesTotal,
                                  BigDecimal total, List<CollectorPaymentResponse> payments) {
}
