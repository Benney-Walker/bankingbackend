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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialSettingsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T09:00:00Z");

    @Mock
    private FinancialSettingsRepo settingsRepo;

    @Mock
    private AuditLogService auditLogService;

    private FinancialSettingsService service;

    @BeforeEach
    void setUp() {
        service = new FinancialSettingsService(settingsRepo, auditLogService);
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    /** The values V31 seeds. */
    private FinancialSettings seeded() {
        return FinancialSettings.builder()
                .id(FinancialSettings.SINGLETON_ID)
                .initialShareAmount(new BigDecimal("50.0000"))
                .withdrawalMinBalance(new BigDecimal("20.0000"))
                .withdrawalDailyLimit(new BigDecimal("10000.0000"))
                .withdrawalFeeEnabled(true)
                .withdrawalFeeType(WithdrawalFeeType.FLAT)
                .withdrawalFeeValue(new BigDecimal("2.0000"))
                .version(0L)
                .updatedAt(Instant.parse("2026-11-14T00:00:00Z"))
                .build();
    }

    private FinancialSettingsUpdateRequest.FinancialSettingsUpdateRequestBuilder sameAsSeeded() {
        return FinancialSettingsUpdateRequest.builder()
                .initialShareAmount(new BigDecimal("50"))
                .withdrawalMinBalance(new BigDecimal("20"))
                .withdrawalDailyLimit(new BigDecimal("10000"))
                .withdrawalFeeEnabled(true)
                .withdrawalFeeType(WithdrawalFeeType.FLAT)
                .withdrawalFeeValue(new BigDecimal("2"))
                .version(0L);
    }

    @Test
    void returnsTheStoredSettings() {
        when(settingsRepo.findById(FinancialSettings.SINGLETON_ID)).thenReturn(Optional.of(seeded()));

        FinancialSettingsResponse response = service.getSettings();

        assertThat(response.initialShareAmount()).isEqualByComparingTo("50");
        assertThat(response.withdrawalFeeType()).isEqualTo(WithdrawalFeeType.FLAT);
        assertThat(response.version()).isZero();
    }

    @Test
    void missingSettingsRowIsAServerError() {
        when(settingsRepo.findById(FinancialSettings.SINGLETON_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.current())
                .isInstanceOf(InternalServerError.class)
                .hasMessage("Financial settings are missing");
    }

    @Test
    void updatesChangedValuesAndRecordsWhoChangedThem() {
        FinancialSettings settings = seeded();
        when(settingsRepo.lockById(FinancialSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepo.saveAndFlush(settings)).thenAnswer(invocation -> {
            settings.setVersion(1L);
            return settings;
        });

        FinancialSettingsResponse response = service.updateSettings(sameAsSeeded()
                .initialShareAmount(new BigDecimal("60"))
                .withdrawalFeeType(WithdrawalFeeType.PERCENTAGE)
                .withdrawalFeeValue(new BigDecimal("1.5"))
                .build(), "900100");

        assertThat(settings.getInitialShareAmount()).isEqualByComparingTo("60");
        assertThat(settings.getWithdrawalFeeType()).isEqualTo(WithdrawalFeeType.PERCENTAGE);
        assertThat(settings.getWithdrawalFeeValue()).isEqualByComparingTo("1.5");
        assertThat(settings.getUpdatedBy()).isEqualTo("900100");
        assertThat(settings.getUpdatedAt()).isEqualTo(NOW);
        assertThat(response.version()).isEqualTo(1L);
        verify(auditLogService).recordAs("900100", AuditAction.FINANCIAL_SETTINGS_UPDATED, AuditTargetType.SETTINGS,
                "financial", "initialShareAmount 50 -> 60; withdrawalFeeType FLAT -> PERCENTAGE; withdrawalFeeValue 2 -> 1.5");
    }

    // An edit made from an out-of-date copy would silently undo someone else's change.
    @Test
    void staleVersionIsRejected() {
        FinancialSettings settings = seeded();
        settings.setVersion(3L);
        when(settingsRepo.lockById(FinancialSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));

        assertThatThrownBy(() -> service.updateSettings(sameAsSeeded().initialShareAmount(BigDecimal.TEN).version(2L).build(), "900100"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The settings were changed by someone else. Reload them and try again.");
        verify(settingsRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    // 50 and 50.0000 are the same amount, so saving the form unchanged does nothing.
    @Test
    void savingWithoutChangesDoesNothing() {
        when(settingsRepo.lockById(FinancialSettings.SINGLETON_ID)).thenReturn(Optional.of(seeded()));

        FinancialSettingsResponse response = service.updateSettings(sameAsSeeded().build(), "900100");

        assertThat(response.version()).isZero();
        verify(settingsRepo, never()).saveAndFlush(any());
        verifyNoInteractions(auditLogService);
    }

    @Test
    void percentageFeeAboveOneHundredIsRejectedBeforeAnyDatabaseWork() {
        assertThatThrownBy(() -> service.updateSettings(sameAsSeeded()
                .withdrawalFeeType(WithdrawalFeeType.PERCENTAGE)
                .withdrawalFeeValue(new BigDecimal("100.01"))
                .build(), "900100"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("A percentage withdrawal fee can't be more than 100");
        verifyNoInteractions(settingsRepo, auditLogService);
    }

    // A flat fee is an amount in GHS, so it can be more than 100.
    @Test
    void flatFeeAboveOneHundredIsAllowed() {
        FinancialSettings settings = seeded();
        when(settingsRepo.lockById(FinancialSettings.SINGLETON_ID)).thenReturn(Optional.of(settings));
        when(settingsRepo.saveAndFlush(settings)).thenReturn(settings);

        service.updateSettings(sameAsSeeded().withdrawalFeeValue(new BigDecimal("150")).build(), "900100");

        assertThat(settings.getWithdrawalFeeValue()).isEqualByComparingTo("150");
    }
}
