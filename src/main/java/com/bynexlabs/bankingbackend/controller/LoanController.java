package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.loan.LoanApplicationRequest;
import com.bynexlabs.bankingbackend.DTO.loan.LoanApplicationResponse;
import com.bynexlabs.bankingbackend.DTO.loan.LoanProductResponse;
import com.bynexlabs.bankingbackend.DTO.loan.LoanResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.ApplicationStatus;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.service.LoanApplicationService;
import com.bynexlabs.bankingbackend.service.LoanProductService;
import com.bynexlabs.bankingbackend.service.LoanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Loans for administrators and agents (owner decisions 2026-11-24). Any branch's member. */
@RestController
@RequestMapping("/api/loan")
@RequiredArgsConstructor
public class LoanController {

    private final AuthenticatedStaffProvider staffProvider;

    private final LoanProductService productService;

    private final LoanApplicationService applicationService;

    private final LoanService loanService;

    /** Loan products: agents see active ones, administrators all. */
    @GetMapping("/v1/products")
    public List<LoanProductResponse> products() {
        return productService.list(staffProvider.hasRole(Roles.ADMINISTRATOR));
    }

    /** Applies for a loan for a member; an administrator then approves or rejects it. */
    @PostMapping("/v1/applications")
    @ResponseStatus(HttpStatus.CREATED)
    public LoanApplicationResponse apply(@Valid @RequestBody LoanApplicationRequest request) {
        return applicationService.apply(request, staffProvider.getStaff());
    }

    /** Applications, newest first: by member, or by status (all when neither is given). */
    @GetMapping("/v1/applications")
    public PagedModel<LoanApplicationResponse> applications(@RequestParam(required = false) ApplicationStatus status,
                                                            @RequestParam(required = false) UUID memberId,
                                                            @RequestParam(defaultValue = "0") int page,
                                                            @RequestParam(defaultValue = "20") int size) {
        return applicationService.list(status, memberId, page, size);
    }

    @GetMapping("/v1/applications/{applicationId}")
    public LoanApplicationResponse application(@PathVariable UUID applicationId) {
        return applicationService.get(applicationId);
    }

    /** A member's loans, newest first, with paid, outstanding and overdue totals. */
    @GetMapping("/v1/members/{memberId}/loans")
    public List<LoanResponse> memberLoans(@PathVariable UUID memberId) {
        return loanService.forMember(memberId);
    }

    /** One loan with its monthly installments. */
    @GetMapping("/v1/loans/{loanId}")
    public LoanResponse loan(@PathVariable UUID loanId) {
        return loanService.get(loanId);
    }
}
