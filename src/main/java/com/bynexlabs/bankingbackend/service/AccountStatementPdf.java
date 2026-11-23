package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.statement.AccountStatement;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Renders an account statement with the JasperReports template reports/account-statement.jrxml
 * (owner decision 2026-11-23). As in EazyManage, the data comes from DTOs: the statement lines
 * through JRBeanCollectionDataSource and the header and totals as parameters; the template runs
 * no SQL. The template is compiled once, on first use.
 */
@Component
public class AccountStatementPdf {

    static final String TEMPLATE = "/reports/account-statement.jrxml";

    private static final DateTimeFormatter PERIOD = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.ENGLISH);

    private volatile JasperReport compiled;

    public byte[] render(AccountStatement statement) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("creditUnionName", statement.creditUnionName());
        parameters.put("branchName", statement.branchName());
        parameters.put("memberName", statement.memberName());
        parameters.put("accountNumber", statement.accountNumber());
        parameters.put("accountType", statement.accountType());
        parameters.put("periodFrom", statement.from().format(PERIOD));
        parameters.put("periodTo", statement.to().format(PERIOD));
        parameters.put("generatedAt", statement.generatedAt().format(GENERATED));
        parameters.put("openingBalance", statement.openingBalance());
        parameters.put("totalIn", statement.totalIn());
        parameters.put("totalOut", statement.totalOut());
        parameters.put("closingBalance", statement.closingBalance());
        try {
            JasperPrint print = JasperFillManager.fillReport(report(), parameters,
                    new JRBeanCollectionDataSource(statement.lines()));
            return JasperExportManager.exportReportToPdf(print);
        } catch (JRException e) {
            throw new IllegalStateException("Could not build the statement PDF", e);
        }
    }

    private JasperReport report() throws JRException {
        JasperReport report = compiled;
        if (report == null) {
            synchronized (this) {
                report = compiled;
                if (report == null) {
                    try (InputStream template = AccountStatementPdf.class.getResourceAsStream(TEMPLATE)) {
                        if (template == null) {
                            throw new IllegalStateException("Statement template " + TEMPLATE + " is missing");
                        }
                        report = JasperCompileManager.compileReport(template);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not read the statement template", e);
                    }
                    compiled = report;
                }
            }
        }
        return report;
    }
}
