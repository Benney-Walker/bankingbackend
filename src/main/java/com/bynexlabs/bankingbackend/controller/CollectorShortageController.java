package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.collector.CollectorShortageResponse;
import com.bynexlabs.bankingbackend.DTO.collector.ShortageRepaymentRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.service.CollectorShortageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Collector shortages. Under /api/transaction, so ADMINISTRATOR and AGENT (SecurityConfig).
 * Agents for their own branch's collectors; administrators for all (the main office).
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transaction/v1/collector-shortages")
public class CollectorShortageController {

    private final CollectorShortageService shortageService;

    private final AuthenticatedStaffProvider staffProvider;

    /** Collectors who still owe a shortage, largest first. */
    @GetMapping
    public List<CollectorShortageResponse> owing() {
        return shortageService.owing(staffProvider.getStaff(), isAdministrator());
    }

    /** What one collector owes, with their repayments. */
    @GetMapping("/{collectorStaffId}")
    public CollectorShortageResponse summary(@PathVariable String collectorStaffId) {
        return shortageService.summary(collectorStaffId, staffProvider.getStaff(), isAdministrator());
    }

    /** Records cash the collector paid back (at most what's owed). Moves it from 1015 to 1010. */
    @PostMapping("/{collectorStaffId}/repayments")
    public CollectorShortageResponse repay(@PathVariable String collectorStaffId,
                                           @Valid @RequestBody ShortageRepaymentRequest request) {
        return shortageService.repay(collectorStaffId, request, staffProvider.getStaff(), isAdministrator());
    }

    private boolean isAdministrator() {
        return staffProvider.hasRole(Roles.ADMINISTRATOR);
    }
}
