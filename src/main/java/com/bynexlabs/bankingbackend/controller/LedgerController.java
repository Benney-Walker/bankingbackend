package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.ledger.TrialBalanceResponse;
import com.bynexlabs.bankingbackend.service.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** General ledger reports. Under /api/admin, so ADMINISTRATOR only (SecurityConfig). */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/admin/ledger")
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/v1/trial-balance")
    public TrialBalanceResponse trialBalance() {
        return ledgerService.trialBalance();
    }
}
