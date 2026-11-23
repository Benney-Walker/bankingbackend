package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.statement.AccountStatement;
import com.bynexlabs.bankingbackend.DTO.statement.StatementLine;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.TransactionChannel;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Account statements as PDF (owner decisions 2026-11-23):
 * - any account type, for any date range (no limit);
 * - administrators for any member, agents for members of their own branch only;
 * - opening and closing balance, money in and out for the period, and each movement with the
 *   balance after it. Only COMPLETED transactions count; a collector payment is dated by the
 *   phone's createdAt;
 * - every statement generated is recorded in the audit log.
 *
 * The balance before the period is worked out back from the current balance, so it always agrees
 * with the account: current balance minus every completed movement made on or after the start date.
 */
@Service
@RequiredArgsConstructor
public class AccountStatementService {

    private static final DateTimeFormatter LINE_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.ENGLISH);

    private final MemberAccountRepo memberAccountRepo;

    private final TransactionRepo transactionRepo;

    private final AuditLogService auditLogService;

    private final AccountStatementPdf pdf;

    @Value("${app.credit-union.name}")
    private String creditUnionName;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** The statement as a PDF, and an audit entry for it. */
    @Transactional
    public byte[] statementPdf(String accountNumber, LocalDate from, LocalDate to, Staff viewer, boolean viewerIsAdmin) {
        AccountStatement statement = build(accountNumber, from, to, viewer, viewerIsAdmin);
        byte[] file = pdf.render(statement);
        auditLogService.recordAs(viewer.getStaffId(), AuditAction.ACCOUNT_STATEMENT_GENERATED, AuditTargetType.ACCOUNT,
                statement.accountNumber(), "Statement from " + from + " to " + to);
        return file;
    }

    /** Works out the statement without rendering it. */
    @Transactional(readOnly = true)
    public AccountStatement build(String accountNumber, LocalDate from, LocalDate to, Staff viewer, boolean viewerIsAdmin) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from can't be after to");
        }
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BadRequestException("Account number is required");
        }
        MemberAccount account = memberAccountRepo.findWithMemberByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        Member member = account.getMember();
        Branch branch = member.getBranch();
        if (!viewerIsAdmin && (viewer.getBranch() == null || branch == null
                || !viewer.getBranch().getId().equals(branch.getId()))) {
            throw new ForbiddenException("This member is from another branch");
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<Transaction> since = transactionRepo.findCompletedForAccountSince(account.getId(), start);

        // Opening balance: today's balance with every movement since the start date taken back out.
        BigDecimal opening = account.getCurrentBalance();
        for (Transaction transaction : since) {
            for (Movement movement : movements(transaction, account.getId())) {
                opening = opening.subtract(movement.amount());
            }
        }

        List<StatementLine> lines = new ArrayList<>();
        BigDecimal balance = opening;
        BigDecimal totalIn = BigDecimal.ZERO;
        BigDecimal totalOut = BigDecimal.ZERO;
        for (Transaction transaction : since) {
            if (!transaction.getCreatedAt().isBefore(end)) {
                break;
            }
            String date = transaction.getCreatedAt().format(LINE_DATE);
            for (Movement movement : movements(transaction, account.getId())) {
                balance = balance.add(movement.amount());
                if (movement.amount().signum() < 0) {
                    totalOut = totalOut.add(movement.amount().negate());
                    lines.add(new StatementLine(date, movement.details(), movement.amount().negate(), null, balance));
                } else {
                    totalIn = totalIn.add(movement.amount());
                    lines.add(new StatementLine(date, movement.details(), null, movement.amount(), balance));
                }
            }
        }

        return new AccountStatement(creditUnionName, branch == null ? "" : branch.getName(),
                member.getFirstName() + " " + member.getLastName(), account.getAccountNumber(),
                label(account.getAccountType().name()), from, to, LocalDateTime.now(clock),
                opening, totalIn, totalOut, balance, lines);
    }

    /** A change to the account's balance: positive in, negative out. */
    record Movement(String details, BigDecimal amount) {
    }

    /**
     * How a transaction changed this account's balance, mirroring MoneyMovementService and
     * CollectorPaymentApplier. A withdrawal fee is its own line.
     */
    static List<Movement> movements(Transaction transaction, UUID accountId) {
        boolean main = transaction.getMemberAccount() != null && transaction.getMemberAccount().getId().equals(accountId);
        boolean collector = transaction.getChannel() == TransactionChannel.COLLECTOR;
        String note = transaction.getDescription() == null || transaction.getDescription().isBlank()
                ? "" : " - " + transaction.getDescription().trim();
        BigDecimal amount = transaction.getAmount();
        BigDecimal shareAmount = zeroIfNull(transaction.getShareAmount());
        BigDecimal fee = zeroIfNull(transaction.getFeeAmount());

        if (main) {
            return switch (transaction.getTransactionType()) {
                case DEPOSIT -> shareAmount.signum() > 0
                        ? List.of(new Movement("First deposit (GHS " + MoneyMovementService.ghs(shareAmount)
                                + " to shares)" + note, amount.subtract(shareAmount)))
                        : List.of(new Movement((collector ? "Deposit by collector" : "Deposit") + note, amount));
                case SHARES -> List.of(new Movement((collector ? "Share purchase by collector" : "Share purchase") + note, amount));
                case WITHDRAWAL -> fee.signum() > 0
                        ? List.of(new Movement("Withdrawal" + note, amount.negate()), new Movement("Withdrawal fee", fee.negate()))
                        : List.of(new Movement("Withdrawal" + note, amount.negate()));
                case TRANSFER -> List.of(new Movement("Transfer to " + counterpartyNumber(transaction) + note, amount.negate()));
                default -> throw new IllegalStateException("A statement can't show a " + transaction.getTransactionType()
                        + " transaction yet (" + transaction.getId() + ")");
            };
        }
        return switch (transaction.getTransactionType()) {
            case DEPOSIT -> List.of(new Movement("Shares from first deposit", shareAmount));
            case TRANSFER -> List.of(new Movement("Transfer from " + transaction.getMemberAccount().getAccountNumber() + note, amount));
            default -> throw new IllegalStateException("A statement can't show a " + transaction.getTransactionType()
                    + " transaction yet (" + transaction.getId() + ")");
        };
    }

    private static String counterpartyNumber(Transaction transaction) {
        return transaction.getCounterpartyAccount() == null ? "" : transaction.getCounterpartyAccount().getAccountNumber();
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String label(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ENGLISH);
    }
}
