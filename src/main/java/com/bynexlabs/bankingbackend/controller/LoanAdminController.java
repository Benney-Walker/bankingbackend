package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.member.KycRejectionRequest;
import com.bynexlabs.bankingbackend.DTO.loan.LoanApplicationResponse;
import com.bynexlabs.bankingbackend.DTO.loan.LoanApprovalRequest;
import com.bynexlabs.bankingbackend.DTO.loan.LoanProductRequest;
import com.bynexlabs.bankingbackend.DTO.loan.LoanProductResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.service.LoanApplicationService;
import com.bynexlabs.bankingbackend.service.LoanProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Loan administration, ADMINISTRATOR only (owner decisions 2026-11-24). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class LoanAdminController {

    private final AuthenticatedStaffProvider staffProvider;

    private final LoanProductService productService;

    private final LoanApplicationService applicationService;

    @PostMapping("/v1/loan-products")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanProductResponse createProduct(@Valid @RequestBody LoanProductRequest request) {
        return productService.create(request, staffProvider.getStaffId());
    }

    /** Replaces a product's terms, including switching it off (active false). */
    @PutMapping("/v1/loan-products/{productId}")
    public LoanProductResponse updateProduct(@PathVariable Integer productId, @Valid @RequestBody LoanProductRequest request) {
        return productService.update(productId, request, staffProvider.getStaffId());
    }

    /**
     * Approves, optionally a lower amount, and pays the loan into the member's account at once.
     * Body optional. Administrators may approve an application they entered.
     */
    @PostMapping("/v1/loan-applications/{applicationId}/approve")
    public LoanApplicationResponse approve(@PathVariable UUID applicationId,
                                           @Valid @RequestBody(required = false) LoanApprovalRequest request) {
        return applicationService.approve(applicationId, request == null ? null : request.getApprovedAmount(),
                staffProvider.getStaff());
    }

    /** Rejects with a reason. */
    @PostMapping("/v1/loan-applications/{applicationId}/reject")
    public LoanApplicationResponse reject(@PathVariable UUID applicationId, @Valid @RequestBody KycRejectionRequest request) {
        return applicationService.reject(applicationId, request.getReason(), staffProvider.getStaff());
    }
}
