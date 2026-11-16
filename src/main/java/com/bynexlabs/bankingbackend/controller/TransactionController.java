package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.transaction.AccountBalanceResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionRequest;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransferRequest;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.service.MoneyMovementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money movements at the office. Access (ADMINISTRATOR and AGENT) is set for /api/transaction/**
 * in SecurityConfig. Every POST needs an idempotencyKey: re-sending the same request with the
 * same key returns the recorded transaction instead of moving the money again.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transaction")
public class TransactionController {

    private final MoneyMovementService moneyMovementService;

    private final AuthenticatedStaffProvider staffProvider;

    @PostMapping("/v1/deposit")
    public TransactionResponse deposit(@Valid @RequestBody TransactionRequest request) {
        return moneyMovementService.deposit(request, staffProvider.getStaff());
    }

    @PostMapping("/v1/share-purchase")
    public TransactionResponse purchaseShares(@Valid @RequestBody TransactionRequest request) {
        return moneyMovementService.purchaseShares(request, staffProvider.getStaff());
    }

    @PostMapping("/v1/withdrawal")
    public TransactionResponse withdraw(@Valid @RequestBody TransactionRequest request) {
        return moneyMovementService.withdraw(request, staffProvider.getStaff());
    }

    @PostMapping("/v1/transfer")
    public TransactionResponse transfer(@Valid @RequestBody TransferRequest request) {
        return moneyMovementService.transfer(request, staffProvider.getStaff());
    }

    @GetMapping("/v1/account/{accountNumber}")
    public AccountBalanceResponse getAccount(@PathVariable String accountNumber) {
        return moneyMovementService.getAccount(accountNumber);
    }

    @GetMapping("/v1/history/{accountNumber}")
    public PagedModel<TransactionResponse> history(@PathVariable String accountNumber,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return moneyMovementService.history(accountNumber, page, size);
    }
}
