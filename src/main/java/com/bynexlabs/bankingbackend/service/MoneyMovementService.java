package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.transaction.AccountBalanceResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionRequest;
import com.bynexlabs.bankingbackend.DTO.transaction.TransactionResponse;
import com.bynexlabs.bankingbackend.DTO.transaction.TransferRequest;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.TransactionRepo;
import com.bynexlabs.bankingbackend.Utility.FeeRules;
import com.bynexlabs.bankingbackend.Utility.MoneyRules;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.AmountPart;
import com.bynexlabs.bankingbackend.enums.PostingEvent;
import com.bynexlabs.bankingbackend.enums.TransactionStatus;
import com.bynexlabs.bankingbackend.enums.TransactionType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.models.FinancialSettings;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.Transaction;
import com.bynexlabs.bankingbackend.service.PostingService.Posting;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Deposits, share purchases, withdrawals and transfers recorded at the office by administrators
 * and agents (roadmap 5.2–5.7). Collector recording comes later with the collector flow.
 *
 * Every operation runs in one database transaction:
 * 1. lock the account(s) with SELECT ... FOR UPDATE (AccountLockService);
 * 2. if the idempotency key was already used for the same request, return that transaction;
 * 3. check the business rules against the locked balances and the financial settings;
 * 4. update the balances, save the transaction and post its journal lines (PostingService).
 * Either all of it commits or none of it does. Recorded transactions are final (D16).
 *
 * current and available balance move together for now: nothing holds funds yet (D21).
 */
@Service
@RequiredArgsConstructor
public class MoneyMovementService {

    public static final int MAX_PAGE_SIZE = 100;

    private final AccountLockService accountLockService;

    private final TransactionService transactionService;

    private final TransactionRepo transactionRepo;

    private final MemberAccountRepo memberAccountRepo;

    private final FinancialSettingsService settingsService;

    private final PostingService postingService;

    /** The credit union's day for the daily withdrawal limit. Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /**
     * Cash deposit into a savings or current account. A member's first deposit must be at least
     * the minimum first deposit, and the initial share amount from it goes to their shares
     * account. This is recorded as one transaction split across the two accounts.
     */
    @Transactional
    public TransactionResponse deposit(TransactionRequest request, Staff staff) {
        requireStaff(staff);
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());

        AccountLockService.AccountWithShares locked = accountLockService.lockWithSharesAccount(request.getAccountNumber());
        MemberAccount account = locked.account();
        Optional<Transaction> replay = transactionService.findReplay(key, TransactionType.DEPOSIT,
                account.getAccountNumber(), amount);
        if (replay.isPresent()) {
            return TransactionResponse.from(replay.get());
        }
        requireSavingsOrCurrent(account, "Deposits go to a savings or current account; use a share purchase for the shares account");

        LocalDateTime now = LocalDateTime.now(clock);
        Transaction transaction = newTransaction(key, TransactionType.DEPOSIT, amount, account, staff,
                request.getDescription(), now);

        if (hasMadeFirstDeposit(account)) {
            credit(account, amount);
            Transaction saved = transactionService.record(transaction);
            postingService.post(saved, Posting.of(PostingEvent.DEPOSIT, account.getAccountType(), null)
                    .with(AmountPart.PRINCIPAL, amount), now);
            return TransactionResponse.from(saved);
        }

        FinancialSettings settings = settingsService.current();
        if (amount.compareTo(settings.getMinFirstDeposit()) < 0) {
            throw new BadRequestException("The first deposit must be at least GHS " + ghs(settings.getMinFirstDeposit()));
        }
        BigDecimal shares = settings.getInitialShareAmount();
        BigDecimal remainder = amount.subtract(shares);
        MemberAccount sharesAccount = locked.sharesAccount();

        credit(account, remainder);
        transaction.setShareAmount(shares);
        // A share amount of 0 in the settings means no mandatory shares: nothing goes to the shares account.
        if (shares.signum() > 0) {
            credit(sharesAccount, shares);
            transaction.setCounterpartyAccount(sharesAccount);
        }
        Transaction saved = transactionService.record(transaction);
        postingService.post(saved, Posting.of(PostingEvent.FIRST_DEPOSIT, account.getAccountType(), AccountType.SHARES)
                .with(AmountPart.PRINCIPAL, amount)
                .with(AmountPart.SHARE, shares)
                .with(AmountPart.REMAINDER, remainder), now);
        return TransactionResponse.from(saved);
    }

    /** Additional shares bought in cash, into the member's shares account. */
    @Transactional
    public TransactionResponse purchaseShares(TransactionRequest request, Staff staff) {
        requireStaff(staff);
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());

        MemberAccount account = accountLockService.lockWithSharesAccount(request.getAccountNumber()).account();
        Optional<Transaction> replay = transactionService.findReplay(key, TransactionType.SHARES,
                account.getAccountNumber(), amount);
        if (replay.isPresent()) {
            return TransactionResponse.from(replay.get());
        }
        if (account.getAccountType() != AccountType.SHARES) {
            throw new BadRequestException("Share purchases go to the member's shares account");
        }
        requireFirstDepositMade(account, "This member's first transaction must be a deposit");

        LocalDateTime now = LocalDateTime.now(clock);
        credit(account, amount);
        Transaction saved = transactionService.record(newTransaction(key, TransactionType.SHARES, amount, account,
                staff, request.getDescription(), now));
        postingService.post(saved, Posting.of(PostingEvent.SHARE_PURCHASE, AccountType.SHARES, null)
                .with(AmountPart.PRINCIPAL, amount), now);
        return TransactionResponse.from(saved);
    }

    /**
     * Cash withdrawal from a savings or current account, paid at the office. The fee from the
     * settings is taken on top of the amount (owner decision 2026-11-14). The account must keep
     * the minimum balance after the amount and fee, and the daily limit counts the amounts
     * withdrawn from this account today, not the fees.
     */
    @Transactional
    public TransactionResponse withdraw(TransactionRequest request, Staff staff) {
        requireStaff(staff);
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());

        MemberAccount account = accountLockService.lockForTransaction(request.getAccountNumber());
        Optional<Transaction> replay = transactionService.findReplay(key, TransactionType.WITHDRAWAL,
                account.getAccountNumber(), amount);
        if (replay.isPresent()) {
            return TransactionResponse.from(replay.get());
        }
        requireSavingsOrCurrent(account, "Withdrawals are only from savings or current accounts");

        FinancialSettings settings = settingsService.current();
        BigDecimal fee = FeeRules.withdrawalFee(settings, amount);
        BigDecimal left = account.getAvailableBalance().subtract(amount).subtract(fee);
        if (left.compareTo(settings.getWithdrawalMinBalance()) < 0) {
            throw new ConflictException("Insufficient balance: the account must keep at least GHS "
                    + ghs(settings.getWithdrawalMinBalance()) + " after the withdrawal and its fee of GHS " + ghs(fee)
                    + ". The most that can be withdrawn now is GHS " + ghs(maxWithdrawable(account, settings)));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        BigDecimal withdrawnToday = transactionRepo.sumAmounts(account.getId(), TransactionType.WITHDRAWAL,
                TransactionStatus.COMPLETED, today.atStartOfDay(), today.plusDays(1).atStartOfDay());
        if (withdrawnToday.add(amount).compareTo(settings.getWithdrawalDailyLimit()) > 0) {
            BigDecimal remaining = settings.getWithdrawalDailyLimit().subtract(withdrawnToday).max(BigDecimal.ZERO);
            throw new ConflictException("This would go over the daily withdrawal limit of GHS "
                    + ghs(settings.getWithdrawalDailyLimit()) + ". GHS " + ghs(remaining)
                    + " can still be withdrawn from this account today");
        }

        debit(account, amount.add(fee));
        Transaction transaction = newTransaction(key, TransactionType.WITHDRAWAL, amount, account, staff,
                request.getDescription(), now);
        transaction.setFeeAmount(fee);
        Transaction saved = transactionService.record(transaction);
        postingService.post(saved, Posting.of(PostingEvent.WITHDRAWAL, account.getAccountType(), null)
                .with(AmountPart.PRINCIPAL, amount)
                .with(AmountPart.FEE, fee), now);
        return TransactionResponse.from(saved);
    }

    /**
     * Transfer between two savings or current accounts, of the same member or of two members
     * (owner decision 2026-11-14). No fee. The source keeps the minimum balance; the receiving
     * member must already have made their first deposit.
     */
    @Transactional
    public TransactionResponse transfer(TransferRequest request, Staff staff) {
        requireStaff(staff);
        BigDecimal amount = MoneyRules.requireValidAmount(request.getAmount());
        String key = TransactionService.requireValidKey(request.getIdempotencyKey());

        List<MemberAccount> locked = accountLockService.lockForTransfer(request.getFromAccountNumber(),
                request.getToAccountNumber());
        MemberAccount from = locked.get(0);
        MemberAccount to = locked.get(1);
        Optional<Transaction> replay = transactionService.findReplay(key, TransactionType.TRANSFER,
                from.getAccountNumber(), to.getAccountNumber(), amount);
        if (replay.isPresent()) {
            return TransactionResponse.from(replay.get());
        }
        requireSavingsOrCurrent(from, "Transfers are only between savings and current accounts");
        requireSavingsOrCurrent(to, "Transfers are only between savings and current accounts");
        requireFirstDepositMade(to, "The receiving member's first transaction must be a deposit");

        FinancialSettings settings = settingsService.current();
        if (from.getAvailableBalance().subtract(amount).compareTo(settings.getWithdrawalMinBalance()) < 0) {
            BigDecimal max = from.getAvailableBalance().subtract(settings.getWithdrawalMinBalance()).max(BigDecimal.ZERO);
            throw new ConflictException("Insufficient balance: the source account must keep at least GHS "
                    + ghs(settings.getWithdrawalMinBalance()) + ". The most that can be transferred now is GHS " + ghs(max));
        }

        LocalDateTime now = LocalDateTime.now(clock);
        debit(from, amount);
        credit(to, amount);
        Transaction transaction = newTransaction(key, TransactionType.TRANSFER, amount, from, staff,
                request.getDescription(), now);
        transaction.setCounterpartyAccount(to);
        Transaction saved = transactionService.record(transaction);
        postingService.post(saved, Posting.of(PostingEvent.TRANSFER, from.getAccountType(), to.getAccountType())
                .with(AmountPart.PRINCIPAL, amount), now);
        return TransactionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountBalanceResponse getAccount(String accountNumber) {
        return AccountBalanceResponse.from(findAccount(accountNumber));
    }

    /** Newest first; includes transfers into the account and first deposits that bought its shares. */
    @Transactional(readOnly = true)
    public PagedModel<TransactionResponse> history(String accountNumber, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("page must be 0 or more and size between 1 and " + MAX_PAGE_SIZE);
        }
        MemberAccount account = findAccount(accountNumber);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new PagedModel<>(transactionRepo.findForAccount(account.getId(), pageRequest)
                .map(TransactionResponse::from));
    }

    private MemberAccount findAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.isBlank()) {
            throw new BadRequestException("Account number is required");
        }
        return memberAccountRepo.findWithMemberByAccountNumber(accountNumber.trim())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private boolean hasMadeFirstDeposit(MemberAccount account) {
        return transactionRepo.existsByMemberAccount_Member_IdAndTransactionTypeAndStatus(
                account.getMember().getId(), TransactionType.DEPOSIT, TransactionStatus.COMPLETED);
    }

    // Confirmed rule: a member's first transaction is always a deposit.
    private void requireFirstDepositMade(MemberAccount account, String message) {
        if (!hasMadeFirstDeposit(account)) {
            throw new ConflictException(message);
        }
    }

    private static void requireSavingsOrCurrent(MemberAccount account, String message) {
        if (account.getAccountType() != AccountType.SAVINGS && account.getAccountType() != AccountType.CURRENT) {
            throw new BadRequestException(message);
        }
    }

    private static void requireStaff(Staff staff) {
        if (staff == null) {
            throw new UnauthorizedException("Authentication required");
        }
    }

    /** The largest amount whose fee still leaves the minimum balance (flat fee: exact; percentage: rounded down). */
    private static BigDecimal maxWithdrawable(MemberAccount account, FinancialSettings settings) {
        BigDecimal spendable = account.getAvailableBalance().subtract(settings.getWithdrawalMinBalance());
        if (spendable.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (!settings.isWithdrawalFeeEnabled()) {
            return spendable;
        }
        BigDecimal max = switch (settings.getWithdrawalFeeType()) {
            case FLAT -> spendable.subtract(settings.getWithdrawalFeeValue());
            case PERCENTAGE -> spendable.multiply(new BigDecimal("100"))
                    .divide(new BigDecimal("100").add(settings.getWithdrawalFeeValue()), 2, RoundingMode.DOWN);
        };
        return max.max(BigDecimal.ZERO);
    }

    private static void credit(MemberAccount account, BigDecimal amount) {
        account.setCurrentBalance(account.getCurrentBalance().add(amount));
        account.setAvailableBalance(account.getAvailableBalance().add(amount));
    }

    private static void debit(MemberAccount account, BigDecimal amount) {
        account.setCurrentBalance(account.getCurrentBalance().subtract(amount));
        account.setAvailableBalance(account.getAvailableBalance().subtract(amount));
    }

    private static Transaction newTransaction(String key, TransactionType type, BigDecimal amount, MemberAccount account,
                                              Staff staff, String description, LocalDateTime now) {
        return Transaction.builder()
                .idempotencyKey(key)
                .transactionType(type)
                .amount(amount)
                .memberAccount(account)
                .initiatedBy(staff)
                .description(description == null || description.isBlank() ? null : description.trim())
                .status(TransactionStatus.COMPLETED)
                .createdAt(now)
                .postedAt(now)
                .build();
    }

    static String ghs(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
