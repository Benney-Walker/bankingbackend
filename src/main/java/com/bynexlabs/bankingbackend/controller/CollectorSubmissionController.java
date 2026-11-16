package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.collector.ApproveSubmissionRequest;
import com.bynexlabs.bankingbackend.DTO.collector.CollectorSubmissionResponse;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.service.CollectorSubmissionService;
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
 * Office side of collector daily submissions. Under /api/transaction, so ADMINISTRATOR and AGENT
 * (SecurityConfig). Agents see and approve only their own branch's collectors; administrators
 * every branch.
 */
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/transaction/v1/collector-submissions")
public class CollectorSubmissionController {

    private final CollectorSubmissionService submissionService;

    private final AuthenticatedStaffProvider staffProvider;

    @GetMapping
    public PagedModel<CollectorSubmissionResponse> list(@RequestParam(defaultValue = "SUBMITTED") CollectorSubmissionStatus status,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return submissionService.list(status, page, size, staffProvider.getStaff(), isAdministrator());
    }

    /** One submission with the payments it covers. */
    @GetMapping("/{submissionId}")
    public CollectorSubmissionResponse get(@PathVariable UUID submissionId) {
        return submissionService.get(submissionId, staffProvider.getStaff(), isAdministrator());
    }

    /**
     * Approves with the cash counted. Moves it to Cash on Hand, records any shortage or surplus,
     * and signs the collector out until tomorrow.
     */
    @PostMapping("/{submissionId}/approve")
    public CollectorSubmissionResponse approve(@PathVariable UUID submissionId,
                                               @Valid @RequestBody ApproveSubmissionRequest request) {
        return submissionService.approve(submissionId, request.getCountedAmount(), staffProvider.getStaff(),
                isAdministrator());
    }

    private boolean isAdministrator() {
        return staffProvider.hasRole(Roles.ADMINISTRATOR);
    }
}
