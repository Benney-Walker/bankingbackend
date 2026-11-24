package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.report.CollectorCollectionsReport;
import com.bynexlabs.bankingbackend.DTO.report.LoanPortfolioReport;
import com.bynexlabs.bankingbackend.DTO.report.ReportRow;
import com.bynexlabs.bankingbackend.DTO.report.TransactionReport;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Report PDFs (owner decision 2026-11-24: reports as JSON and PDF), all through one landscape table
 * template, reports/table-report.jrxml. The report data is turned into rows here; the template
 * runs no SQL. Compiled once, on first use.
 */
@Component
public class ReportPdf {

    static final String TEMPLATE = "/reports/table-report.jrxml";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    @Value("${app.credit-union.name}")
    private String creditUnionName;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    private volatile JasperReport compiled;

    public byte[] transactions(TransactionReport report) {
        List<ReportRow> rows = new ArrayList<>();
        rows.add(ReportRow.header("Date", "Branch", "Type", "Count", "Amount (GHS)", "Fees (GHS)"));
        for (TransactionReport.Row r : report.rows()) {
            rows.add(ReportRow.row(day(r.date()), r.branchName(), label(r.type().name()), String.valueOf(r.count()),
                    money(r.amount()), money(r.fees())));
        }
        rows.add(ReportRow.section("Totals by type"));
        rows.add(ReportRow.header("Type", "", "", "Count", "Amount (GHS)", "Fees (GHS)"));
        for (TransactionReport.TypeTotal t : report.totals()) {
            rows.add(ReportRow.row(label(t.type().name()), "", "", String.valueOf(t.count()), money(t.amount()), money(t.fees())));
        }
        rows.add(ReportRow.total("All transactions", "", "", String.valueOf(report.count()), money(report.amount()),
                money(report.fees())));
        return render("Daily transactions", report.branchName() + ", " + period(report.from(), report.to()), rows);
    }

    public byte[] collectorCollections(CollectorCollectionsReport report) {
        List<ReportRow> rows = new ArrayList<>();
        rows.add(ReportRow.section("Payments recorded"));
        rows.add(ReportRow.header("Date", "Collector", "Branch", "Payments", "Amount (GHS)", "Failed", "Failed (GHS)"));
        for (CollectorCollectionsReport.Day d : report.days()) {
            rows.add(ReportRow.row(day(d.date()), d.collectorName() + " (" + d.collectorStaffId() + ")", d.branchName(),
                    String.valueOf(d.payments()), money(d.amount()), String.valueOf(d.failedPayments()), money(d.failedAmount())));
        }
        rows.add(ReportRow.total("Collected", "", "", "", money(report.collected())));
        rows.add(ReportRow.section("Daily submissions"));
        rows.add(ReportRow.header("Submitted", "Collector", "Status", "Payments", "Expected (GHS)", "Counted (GHS)",
                "Surplus (GHS)", "Approved by"));
        for (CollectorCollectionsReport.Submission s : report.submissions()) {
            rows.add(ReportRow.row(s.submittedAt().format(MINUTE), s.collectorName() + " (" + s.collectorStaffId() + ")",
                    label(s.status().name()), String.valueOf(s.payments()), money(s.expected()),
                    s.counted() == null ? "" : money(s.counted()), money(s.surplus()),
                    s.approvedBy() == null ? "" : s.approvedBy()));
        }
        rows.add(ReportRow.total("Totals", "", "", "", money(report.expected()), money(report.counted()),
                money(report.surplus())));
        return render("Collector collections", report.branchName() + ", " + period(report.from(), report.to()), rows);
    }

    public byte[] loanPortfolio(LoanPortfolioReport report) {
        List<ReportRow> rows = new ArrayList<>();
        rows.add(ReportRow.header("Member", "Product", "Paid out", "Amount (GHS)", "To repay (GHS)", "Paid (GHS)",
                "Outstanding (GHS)", "Overdue (GHS)"));
        for (LoanPortfolioReport.Loan l : report.loans()) {
            rows.add(ReportRow.row(l.memberName(), l.productName(), day(l.disbursementDate()), money(l.amount()),
                    money(l.totalToRepay()), money(l.paid()), money(l.outstanding()), money(l.overdue())));
        }
        rows.add(ReportRow.total(report.activeLoans() + " active loan(s)", report.overdueLoans() + " overdue", "",
                money(report.amountLent()), "", "", money(report.outstanding()), money(report.overdue())));
        return render("Loan portfolio", report.branchName() + ", as of " + report.asOf().format(MINUTE), rows);
    }

    private byte[] render(String title, String subtitle, List<ReportRow> rows) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("creditUnionName", creditUnionName);
        parameters.put("reportTitle", title);
        parameters.put("reportSubtitle", subtitle);
        parameters.put("generatedAt", LocalDateTime.now(clock).format(MINUTE));
        try {
            JasperPrint print = JasperFillManager.fillReport(report(), parameters, new JRBeanCollectionDataSource(rows));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new IllegalStateException("Could not build the report PDF", e);
        }
    }

    private JasperReport report() throws JRException {
        JasperReport report = compiled;
        if (report == null) {
            synchronized (this) {
                report = compiled;
                if (report == null) {
                    try (InputStream template = ReportPdf.class.getResourceAsStream(TEMPLATE)) {
                        if (template == null) {
                            throw new IllegalStateException("Report template " + TEMPLATE + " is missing");
                        }
                        report = JasperCompileManager.compileReport(template);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not read the report template", e);
                    }
                    compiled = report;
                }
            }
        }
        return report;
    }

    static String money(BigDecimal amount) {
        DecimalFormat format = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        format.setRoundingMode(RoundingMode.HALF_UP);
        return format.format(amount == null ? BigDecimal.ZERO : amount);
    }

    private static String day(LocalDate date) {
        return date == null ? "" : date.format(DAY);
    }

    private static String period(LocalDate from, LocalDate to) {
        return from.equals(to) ? day(from) : day(from) + " to " + day(to);
    }

    /** COLLECTOR_HANDOVER → Collector handover */
    static String label(String name) {
        String words = name.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }
}
