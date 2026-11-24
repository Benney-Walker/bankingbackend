package com.bynexlabs.bankingbackend.DTO.report;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * One line of a report PDF (reports/table-report.jrxml). A plain bean with getters for
 * JRBeanCollectionDataSource. kind is SECTION (a heading in c1), HEADER (column names), ROW or TOTAL.
 */
@Getter
@AllArgsConstructor
public class ReportRow {

    private final String kind;
    private final String c1;
    private final String c2;
    private final String c3;
    private final String c4;
    private final String c5;
    private final String c6;
    private final String c7;
    private final String c8;

    public static ReportRow section(String title) {
        return new ReportRow("SECTION", title, null, null, null, null, null, null, null);
    }

    public static ReportRow header(String... columns) {
        return of("HEADER", columns);
    }

    public static ReportRow row(String... columns) {
        return of("ROW", columns);
    }

    public static ReportRow total(String... columns) {
        return of("TOTAL", columns);
    }

    private static ReportRow of(String kind, String... columns) {
        String[] c = new String[8];
        System.arraycopy(columns, 0, c, 0, Math.min(columns.length, 8));
        return new ReportRow(kind, c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]);
    }
}
