package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.report.CollectorCollectionsReport;
import com.bynexlabs.bankingbackend.DTO.report.LoanPortfolioReport;
import com.bynexlabs.bankingbackend.DTO.report.TransactionReport;
import com.bynexlabs.bankingbackend.Repository.BranchRepo;
import com.bynexlabs.bankingbackend.enums.CollectorSubmissionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ForbiddenException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.Branch;
import com.bynexlabs.bankingbackend.models.Staff;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operational reports (owner decisions 2026-11-24): daily transactions, collector collections and
 * the loan portfolio. Administrators see every branch or one they pick; agents only their own
 * branch (another branch → 403). Read straight from the tables with grouped SQL, so a long range
 * doesn't load every transaction into memory.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EntityManager entityManager;

    private final BranchRepo branchRepo;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** Which branch a report covers: null branch means every branch (administrators only). */
    record Scope(UUID branchId, String branchName) {
    }

    Scope scope(Staff viewer, boolean viewerIsAdmin, UUID requestedBranchId) {
        if (viewerIsAdmin) {
            if (requestedBranchId == null) {
                return new Scope(null, "All branches");
            }
            Branch branch = branchRepo.findById(requestedBranchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
            return new Scope(branch.getId(), branch.getName());
        }
        Branch own = viewer.getBranch();
        if (own == null) {
            throw new ForbiddenException("Your account has no branch");
        }
        if (requestedBranchId != null && !requestedBranchId.equals(own.getId())) {
            throw new ForbiddenException("Agents can only see their own branch's reports");
        }
        return new Scope(own.getId(), own.getName());
    }

    @Transactional(readOnly = true)
    public TransactionReport transactions(LocalDate from, LocalDate to, UUID branchId, Staff viewer, boolean viewerIsAdmin) {
        requireRange(from, to);
        Scope scope = scope(viewer, viewerIsAdmin, branchId);
        String sql = """
                select to_char(t.created_at, 'YYYY-MM-DD'), cast(coalesce(m.branch, s.branch) as text), b.name,
                       t.transaction_type, count(*), coalesce(sum(t.amount), 0), coalesce(sum(t.fee_amount), 0)
                from transaction t
                left join member_account ma on ma.id = t.member_account_id
                left join member m on m.id = ma.member_id
                left join staffs s on s.id = t.initiated_by
                left join branch_table b on b.id = coalesce(m.branch, s.branch)
                where t.status = 'COMPLETED' and t.created_at >= :from and t.created_at < :to
                """ + (scope.branchId() == null ? "" : " and coalesce(m.branch, s.branch) = :branchId") + """

                group by 1, 2, 3, 4
                order by 1, 3, 4
                """;
        List<Object[]> result = rows(sql, from, to, scope);

        List<TransactionReport.Row> rows = new ArrayList<>();
        Map<TransactionType, TransactionReport.TypeTotal> byType = new EnumMap<>(TransactionType.class);
        long count = 0;
        BigDecimal amount = BigDecimal.ZERO;
        BigDecimal fees = BigDecimal.ZERO;
        for (Object[] r : result) {
            TransactionType type = TransactionType.valueOf((String) r[3]);
            long n = number(r[4]).longValue();
            BigDecimal sum = decimal(r[5]);
            BigDecimal fee = decimal(r[6]);
            rows.add(new TransactionReport.Row(LocalDate.parse((String) r[0]), uuid(r[1]), (String) r[2], type, n, sum, fee));
            byType.merge(type, new TransactionReport.TypeTotal(type, n, sum, fee), (a, b) ->
                    new TransactionReport.TypeTotal(type, a.count() + b.count(), a.amount().add(b.amount()), a.fees().add(b.fees())));
            count += n;
            amount = amount.add(sum);
            fees = fees.add(fee);
        }
        return new TransactionReport(from, to, scope.branchId(), scope.branchName(), rows, List.copyOf(byType.values()),
                count, amount, fees);
    }

    @Transactional(readOnly = true)
    public CollectorCollectionsReport collectorCollections(LocalDate from, LocalDate to, UUID branchId, Staff viewer,
                                                           boolean viewerIsAdmin) {
        requireRange(from, to);
        Scope scope = scope(viewer, viewerIsAdmin, branchId);
        String daySql = """
                select to_char(t.created_at, 'YYYY-MM-DD'), s.staff_id, s.first_name || ' ' || s.last_name, b.name,
                       count(*) filter (where t.status = 'COMPLETED'),
                       coalesce(sum(t.amount) filter (where t.status = 'COMPLETED'), 0),
                       count(*) filter (where t.status = 'FAILED'),
                       coalesce(sum(t.amount) filter (where t.status = 'FAILED'), 0)
                from transaction t
                join staffs s on s.id = t.initiated_by
                left join branch_table b on b.id = s.branch
                where t.channel = 'COLLECTOR' and t.created_at >= :from and t.created_at < :to
                """ + (scope.branchId() == null ? "" : " and s.branch = :branchId") + """

                group by 1, 2, 3, 4
                order by 1, 4, 3
                """;
        List<CollectorCollectionsReport.Day> days = new ArrayList<>();
        BigDecimal collected = BigDecimal.ZERO;
        for (Object[] r : rows(daySql, from, to, scope)) {
            BigDecimal amount = decimal(r[5]);
            days.add(new CollectorCollectionsReport.Day(LocalDate.parse((String) r[0]), (String) r[1], (String) r[2],
                    (String) r[3], number(r[4]).longValue(), amount, number(r[6]).longValue(), decimal(r[7])));
            collected = collected.add(amount);
        }

        String submissionSql = """
                select to_char(cs.submitted_at, 'YYYY-MM-DD HH24:MI'), s.staff_id, s.first_name || ' ' || s.last_name,
                       b.name, cs.payment_count, cs.expected_amount, cs.failed_count, cs.counted_amount,
                       cs.overage_amount, cs.status, a.staff_id, to_char(cs.approved_at, 'YYYY-MM-DD HH24:MI')
                from collector_submission cs
                join staffs s on s.id = cs.collector_id
                left join branch_table b on b.id = cs.branch_id
                left join staffs a on a.id = cs.approved_by
                where cs.submitted_at >= :from and cs.submitted_at < :to
                """ + (scope.branchId() == null ? "" : " and cs.branch_id = :branchId") + """

                order by cs.submitted_at
                """;
        List<CollectorCollectionsReport.Submission> submissions = new ArrayList<>();
        BigDecimal expected = BigDecimal.ZERO;
        BigDecimal counted = BigDecimal.ZERO;
        BigDecimal surplus = BigDecimal.ZERO;
        for (Object[] r : rows(submissionSql, from, to, scope)) {
            BigDecimal exp = decimal(r[5]);
            BigDecimal cnt = r[7] == null ? null : decimal(r[7]);
            BigDecimal over = r[8] == null ? BigDecimal.ZERO : decimal(r[8]);
            submissions.add(new CollectorCollectionsReport.Submission(LocalDateTime.parse((String) r[0], MINUTE),
                    (String) r[1], (String) r[2], (String) r[3], number(r[4]).intValue(), exp, number(r[6]).intValue(),
                    cnt, over, CollectorSubmissionStatus.valueOf((String) r[9]), (String) r[10],
                    r[11] == null ? null : LocalDateTime.parse((String) r[11], MINUTE)));
            expected = expected.add(exp);
            counted = cnt == null ? counted : counted.add(cnt);
            surplus = surplus.add(over);
        }
        return new CollectorCollectionsReport(from, to, scope.branchId(), scope.branchName(), days, submissions,
                collected, expected, counted, surplus);
    }

    @Transactional(readOnly = true)
    public LoanPortfolioReport loanPortfolio(UUID branchId, Staff viewer, boolean viewerIsAdmin) {
        Scope scope = scope(viewer, viewerIsAdmin, branchId);
        String sql = """
                select cast(l.id as text), cast(m.id as text), m.first_name || ' ' || m.last_name, b.name, p.name,
                       to_char(l.disbursement_date, 'YYYY-MM-DD'), l.principal_amount, l.principal_amount + l.total_interest,
                       coalesce(sum(s.paid_principal + s.paid_interest), 0),
                       coalesce(sum(s.expected_total - s.paid_principal - s.paid_interest), 0),
                       coalesce(sum(s.expected_total - s.paid_principal - s.paid_interest) filter (where s.status = 'OVERDUE'), 0),
                       to_char(min(s.due_date) filter (where s.status <> 'PAID'), 'YYYY-MM-DD')
                from loan_account l
                join member m on m.id = l.member_id
                left join branch_table b on b.id = m.branch
                join loan_application a on a.id = l.loan_application_id
                join loan_product p on p.id = a.loan_product_id
                left join amortization_schedule s on s.loan_account_id = l.id
                where l.status = 'ACTIVE'
                """ + (scope.branchId() == null ? "" : " and m.branch = :branchId") + """

                group by l.id, m.id, m.first_name, m.last_name, b.name, p.name, l.disbursement_date,
                         l.principal_amount, l.total_interest
                order by b.name, m.last_name, m.first_name
                """;
        Query query = entityManager.createNativeQuery(sql);
        if (scope.branchId() != null) {
            query.setParameter("branchId", scope.branchId());
        }
        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();

        List<LoanPortfolioReport.Loan> loans = new ArrayList<>();
        int overdueLoans = 0;
        BigDecimal lent = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        for (Object[] r : result) {
            BigDecimal amount = decimal(r[6]);
            BigDecimal left = decimal(r[9]);
            BigDecimal late = decimal(r[10]);
            loans.add(new LoanPortfolioReport.Loan(uuid(r[0]), uuid(r[1]), (String) r[2], (String) r[3], (String) r[4],
                    LocalDate.parse((String) r[5]), amount, decimal(r[7]), decimal(r[8]), left, late,
                    r[11] == null ? null : LocalDate.parse((String) r[11])));
            lent = lent.add(amount);
            outstanding = outstanding.add(left);
            overdue = overdue.add(late);
            if (late.signum() > 0) {
                overdueLoans++;
            }
        }
        return new LoanPortfolioReport(LocalDateTime.now(clock), scope.branchId(), scope.branchName(), loans,
                loans.size(), overdueLoans, lent, outstanding, overdue);
    }

    private List<Object[]> rows(String sql, LocalDate from, LocalDate to, Scope scope) {
        Query query = entityManager.createNativeQuery(sql)
                .setParameter("from", from.atStartOfDay())
                .setParameter("to", to.plusDays(1).atStartOfDay());
        if (scope.branchId() != null) {
            query.setParameter("branchId", scope.branchId());
        }
        @SuppressWarnings("unchecked")
        List<Object[]> result = query.getResultList();
        return result;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new BadRequestException("from and to dates are required");
        }
        if (from.isAfter(to)) {
            throw new BadRequestException("from can't be after to");
        }
    }

    private static Number number(Object value) {
        return value == null ? 0 : (Number) value;
    }

    private static BigDecimal decimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
    }

    private static UUID uuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }
}
