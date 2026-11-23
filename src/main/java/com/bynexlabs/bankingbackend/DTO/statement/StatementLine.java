package com.bynexlabs.bankingbackend.DTO.statement;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * One row of an account statement. A plain bean with getters, because the JasperReports template
 * reads the rows through JRBeanCollectionDataSource. moneyOut or moneyIn is null on each row.
 */
@Getter
@AllArgsConstructor
public class StatementLine {

    /** When the money moved, already formatted (dd/MM/yyyy HH:mm, Africa/Accra). */
    private final String date;

    private final String details;

    private final BigDecimal moneyOut;

    private final BigDecimal moneyIn;

    /** The account balance after this row. */
    private final BigDecimal balance;
}
