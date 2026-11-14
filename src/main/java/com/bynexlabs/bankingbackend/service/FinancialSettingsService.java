package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.settings.FinancialSettingsResponse;
import com.bynexlabs.bankingbackend.DTO.settings.FinancialSettingsUpdateRequest;
import com.bynexlabs.bankingbackend.Repository.FinancialSettingsRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.WithdrawalFeeType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.exception.InternalServerError;
import com.bynexlabs.bankingbackend.models.FinancialSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reads and updates the credit union's financial settings (V31, V32): the mandatory first-deposit
 * share amount, the minimum first deposit and the withdrawal rules. They're data, not code, so administrators can change
 * them from the desktop app (owner decision 2026-11-14). The seeded values are mock values.
 *
 * Money movements should read the settings inside their own transaction, so a deposit or
 * withdrawal uses one consistent set of values from start to finish.
 */
@Service
@RequiredArgsConstructor
public class FinancialSettingsService {

    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final FinancialSettingsRepo settingsRepo;

    private final AuditLogService auditLogService;

    /** Replaceable in tests. */
    Clock clock = Clock.systemUTC();

    /** The current settings, for business logic. */
    @Transactional(readOnly = true)
    public FinancialSettings current() {
        return settingsRepo.findById(FinancialSettings.SINGLETON_ID)
                .orElseThrow(FinancialSettingsService::missingRow);
    }

    @Transactional(readOnly = true)
    public FinancialSettingsResponse getSettings() {
        return FinancialSettingsResponse.from(current());
    }

    /**
     * Replaces the settings. The request's version must match the stored one: if another
     * administrator saved in between, this edit is rejected (409) so they can reload and retry.
     * The change is recorded in the audit log with old and new values.
     */
    @Transactional
    public FinancialSettingsResponse updateSettings(FinancialSettingsUpdateRequest request, String editorStaffId) {
        // The share purchase comes out of the first deposit, so the minimum has to cover it.
        if (request.getMinFirstDeposit().compareTo(request.getInitialShareAmount()) < 0) {
            throw new BadRequestException("The minimum first deposit can't be less than the initial share amount");
        }
        if (request.getWithdrawalFeeType() == WithdrawalFeeType.PERCENTAGE
                && request.getWithdrawalFeeValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new BadRequestException("A percentage withdrawal fee can't be more than 100");
        }

        FinancialSettings settings = settingsRepo.lockById(FinancialSettings.SINGLETON_ID)
                .orElseThrow(FinancialSettingsService::missingRow);
        if (!Objects.equals(settings.getVersion(), request.getVersion())) {
            throw new ConflictException("The settings were changed by someone else. Reload them and try again.");
        }

        List<String> changes = new ArrayList<>();
        track(changes, "initialShareAmount", settings.getInitialShareAmount(), request.getInitialShareAmount());
        track(changes, "minFirstDeposit", settings.getMinFirstDeposit(), request.getMinFirstDeposit());
        track(changes, "withdrawalMinBalance", settings.getWithdrawalMinBalance(), request.getWithdrawalMinBalance());
        track(changes, "withdrawalDailyLimit", settings.getWithdrawalDailyLimit(), request.getWithdrawalDailyLimit());
        track(changes, "withdrawalFeeEnabled", settings.isWithdrawalFeeEnabled(), request.getWithdrawalFeeEnabled());
        track(changes, "withdrawalFeeType", settings.getWithdrawalFeeType(), request.getWithdrawalFeeType());
        track(changes, "withdrawalFeeValue", settings.getWithdrawalFeeValue(), request.getWithdrawalFeeValue());

        // Saving without changes is a no-op, so the version doesn't move and nothing is logged.
        if (changes.isEmpty()) {
            return FinancialSettingsResponse.from(settings);
        }

        settings.setInitialShareAmount(request.getInitialShareAmount());
        settings.setMinFirstDeposit(request.getMinFirstDeposit());
        settings.setWithdrawalMinBalance(request.getWithdrawalMinBalance());
        settings.setWithdrawalDailyLimit(request.getWithdrawalDailyLimit());
        settings.setWithdrawalFeeEnabled(request.getWithdrawalFeeEnabled());
        settings.setWithdrawalFeeType(request.getWithdrawalFeeType());
        settings.setWithdrawalFeeValue(request.getWithdrawalFeeValue());
        settings.setUpdatedAt(Instant.now(clock));
        settings.setUpdatedBy(editorStaffId);

        // saveAndFlush so the response carries the incremented version.
        FinancialSettings saved = settingsRepo.saveAndFlush(settings);
        // These are credit-union settings, not personal data, so old and new values are logged.
        auditLogService.recordAs(editorStaffId, AuditAction.FINANCIAL_SETTINGS_UPDATED,
                AuditTargetType.SETTINGS, "financial", String.join("; ", changes));
        return FinancialSettingsResponse.from(saved);
    }

    private static void track(List<String> changes, String field, Object oldValue, Object newValue) {
        boolean same = oldValue instanceof BigDecimal oldAmount && newValue instanceof BigDecimal newAmount
                ? oldAmount.compareTo(newAmount) == 0
                : Objects.equals(oldValue, newValue);
        if (!same) {
            changes.add(field + " " + plain(oldValue) + " -> " + plain(newValue));
        }
    }

    private static String plain(Object value) {
        return value instanceof BigDecimal amount ? amount.stripTrailingZeros().toPlainString() : String.valueOf(value);
    }

    private static InternalServerError missingRow() {
        // V31 seeds the row, so this only happens if it was deleted by hand.
        return new InternalServerError("Financial settings are missing");
    }
}
