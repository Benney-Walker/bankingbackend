package com.bynexlabs.bankingbackend.controller;

import com.bynexlabs.bankingbackend.DTO.report.CollectorCollectionsReport;
import com.bynexlabs.bankingbackend.DTO.report.LoanPortfolioReport;
import com.bynexlabs.bankingbackend.DTO.report.TransactionReport;
import com.bynexlabs.bankingbackend.Utility.AuthenticatedStaffProvider;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.service.ReportPdf;
import com.bynexlabs.bankingbackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Reports as JSON, or as a PDF download at the same path plus /pdf (owner decisions 2026-11-24).
 * Administrators: every branch, or one with branchId. Agents: their own branch only.
 */
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final AuthenticatedStaffProvider staffProvider;

    private final ReportService reportService;

    private final ReportPdf reportPdf;

    @GetMapping("/v1/transactions")
    public TransactionReport transactions(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                          @RequestParam(required = false) UUID branchId) {
        return reportService.transactions(from, to, branchId, staffProvider.getStaff(), isAdmin());
    }

    @GetMapping("/v1/transactions/pdf")
    public ResponseEntity<byte[]> transactionsPdf(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  @RequestParam(required = false) UUID branchId) {
        return pdf(reportPdf.transactions(transactions(from, to, branchId)), "transactions-" + from + "-" + to);
    }

    @GetMapping("/v1/collector-collections")
    public CollectorCollectionsReport collectorCollections(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return reportService.collectorCollections(from, to, branchId, staffProvider.getStaff(), isAdmin());
    }

    @GetMapping("/v1/collector-collections/pdf")
    public ResponseEntity<byte[]> collectorCollectionsPdf(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID branchId) {
        return pdf(reportPdf.collectorCollections(collectorCollections(from, to, branchId)),
                "collector-collections-" + from + "-" + to);
    }

    /** Loans being repaid now. */
    @GetMapping("/v1/loan-portfolio")
    public LoanPortfolioReport loanPortfolio(@RequestParam(required = false) UUID branchId) {
        return reportService.loanPortfolio(branchId, staffProvider.getStaff(), isAdmin());
    }

    @GetMapping("/v1/loan-portfolio/pdf")
    public ResponseEntity<byte[]> loanPortfolioPdf(@RequestParam(required = false) UUID branchId) {
        return pdf(reportPdf.loanPortfolio(loanPortfolio(branchId)), "loan-portfolio-" + LocalDate.now());
    }

    private boolean isAdmin() {
        return staffProvider.hasRole(Roles.ADMINISTRATOR);
    }

    private static ResponseEntity<byte[]> pdf(byte[] file, String name) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name + ".pdf").build().toString())
                .body(file);
    }
}
