package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorCorrectionRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorDaySummary;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorMemberResponse;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorPaymentResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.service.CollectorPaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The collector app's API. /api/collector/** is COLLECTOR-only (SecurityConfig); every action
 * works on the signed-in collector's own branch and records. Collectors only take payments:
 * members withdraw at any branch's office (owner decision 2026-11-15).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/collector")
public class CollectorController {

    private final CollectorPaymentService paymentService;

    private final AuthenticatedStaffProvider staffProvider;

    /** Members in the collector's branch with their savings and shares accounts, for the app's offline list. */
    @GetMapping("/v1/members")
    public List<CollectorMemberResponse> members() {
        return paymentService.members(staffProvider.getStaff());
    }

    /** A savings deposit or share purchase: `type` is SAVINGS or SHARES. */
    @PostMapping("/v1/payments")
    public CollectorPaymentResponse recordPayment(@Valid @RequestBody CollectorPaymentRequest request) {
        return paymentService.record(request, staffProvider.getStaff());
    }

    /** Correction within 10 minutes of saving: new amount and/or the member's other account. */
    @PutMapping("/v1/payments/{transactionId}")
    public CollectorPaymentResponse correctPayment(@PathVariable UUID transactionId,
                                                   @RequestBody CollectorCorrectionRequest request) {
        return paymentService.correct(transactionId, request, staffProvider.getStaff());
    }

    /** The collector's own payments for a day (default today) with totals. */
    @GetMapping("/v1/payments")
    public CollectorDaySummary payments(@RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return paymentService.daySummary(staffProvider.getStaff(), date);
    }
}
