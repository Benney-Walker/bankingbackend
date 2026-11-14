package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.collector.PayWithdrawalRequest;
import com.bynexlabs.bankingbackend.DTO.collector.RejectWithdrawalRequest;
import com.bynexlabs.bankingbackend.DTO.collector.WithdrawalRequestResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.WithdrawalRequestStatus;
import com.bynexlabs.bankingbackend.service.WithdrawalRequestService;
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

import java.util.UUID;

/**
 * Office side of collector withdrawal requests: review, then pay when the member comes in.
 * Under /api/transaction, so ADMINISTRATOR and AGENT (SecurityConfig); agents only for their
 * own branch's requests.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transaction/v1/withdrawal-requests")
public class WithdrawalRequestController {

    private final WithdrawalRequestService withdrawalRequestService;

    private final AuthenticatedStaffProvider staffProvider;

    @GetMapping
    public PagedModel<WithdrawalRequestResponse> list(@RequestParam(defaultValue = "PENDING") WithdrawalRequestStatus status,
                                                      @RequestParam(defaultValue = "0") int page,
                                                      @RequestParam(defaultValue = "20") int size) {
        return withdrawalRequestService.list(status, page, size, staffProvider.getStaff(), isAdministrator());
    }

    @PostMapping("/{requestId}/approve")
    public WithdrawalRequestResponse approve(@PathVariable UUID requestId) {
        return withdrawalRequestService.approve(requestId, staffProvider.getStaff(), isAdministrator());
    }

    @PostMapping("/{requestId}/reject")
    public WithdrawalRequestResponse reject(@PathVariable UUID requestId, @Valid @RequestBody RejectWithdrawalRequest request) {
        return withdrawalRequestService.reject(requestId, request.getReason(), staffProvider.getStaff(),
                isAdministrator());
    }

    @PostMapping("/{requestId}/pay")
    public WithdrawalRequestResponse pay(@PathVariable UUID requestId, @Valid @RequestBody PayWithdrawalRequest request) {
        return withdrawalRequestService.pay(requestId, request.getIdempotencyKey(), staffProvider.getStaff(),
                isAdministrator());
    }

    // Administrators act on every branch's requests; agents only on their own branch's.
    private boolean isAdministrator() {
        return staffProvider.hasRole(Roles.ADMINISTRATOR);
    }
}
