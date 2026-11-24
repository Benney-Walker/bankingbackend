package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.loan.LoanProductRequest;
import com.bynexlabs.bankingbackend.DTO.loan.LoanProductResponse;
import com.bynexlabs.bankingbackend.Repository.LoanProductRepo;
import com.bynexlabs.bankingbackend.enums.AuditAction;
import com.bynexlabs.bankingbackend.enums.AuditTargetType;
import com.bynexlabs.bankingbackend.enums.InterestType;
import com.bynexlabs.bankingbackend.enums.LoanFeeType;
import com.bynexlabs.bankingbackend.exception.BadRequestException;
import com.bynexlabs.bankingbackend.exception.ConflictException;
import com.bynexlabs.bankingbackend.models.LoanProduct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoanProductServiceTest {

    @Mock private LoanProductRepo productRepo;
    @Mock private AuditLogService auditLogService;

    private LoanProductService service;

    @BeforeEach
    void setUp() {
        service = new LoanProductService(productRepo, auditLogService);
    }

    private static LoanProductRequest request() {
        return LoanProductRequest.builder().name(" Susu Loan ").interestRatePercent(new BigDecimal("12.5"))
                .minAmount(new BigDecimal("100")).maxAmount(new BigDecimal("5000")).maxTermMonths(12)
                .processingFeeType(LoanFeeType.PERCENTAGE).processingFeeValue(new BigDecimal("2")).guarantorsRequired(1)
                .active(true).build();
    }

    @Test
    void createStoresTheYearlyRateAsAFractionAndIsAudited() {
        when(productRepo.save(any(LoanProduct.class))).thenAnswer(i -> {
            LoanProduct p = i.getArgument(0);
            p.setId(7);
            return p;
        });

        LoanProductResponse response = service.create(request(), "900001");

        ArgumentCaptor<LoanProduct> saved = ArgumentCaptor.forClass(LoanProduct.class);
        verify(productRepo).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Susu Loan");
        assertThat(saved.getValue().getBaseInterestRate()).isEqualByComparingTo("0.1250");
        assertThat(saved.getValue().getInterestType()).isEqualTo(InterestType.FLAT);
        assertThat(response.interestRatePercent()).isEqualByComparingTo("12.50");
        verify(auditLogService).recordAs(eq("900001"), eq(AuditAction.LOAN_PRODUCT_CREATED),
                eq(AuditTargetType.LOAN_PRODUCT), eq("7"),
                startsWith("Susu Loan: 12.5% a year flat"));
    }

    @Test
    void namesAreUnique() {
        when(productRepo.existsByNameIgnoreCase("Susu Loan")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(), "900001")).isInstanceOf(ConflictException.class);
        verify(productRepo, never()).save(any());
    }

    @Test
    void maximumCantBeBelowMinimum() {
        LoanProductRequest request = request();
        request.setMaxAmount(new BigDecimal("50"));

        assertThatThrownBy(() -> service.create(request, "900001")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void flatFeeMustLeaveSomethingToPayOut() {
        LoanProductRequest request = request();
        request.setProcessingFeeType(LoanFeeType.FLAT);
        request.setProcessingFeeValue(new BigDecimal("100"));

        assertThatThrownBy(() -> service.create(request, "900001")).isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateWithNoChangesIsNotAudited() {
        LoanProduct product = LoanProduct.builder().id(7).name("Susu Loan").baseInterestRate(new BigDecimal("0.1250"))
                .minAmount(new BigDecimal("100")).maxAmount(new BigDecimal("5000")).maxTermMonths(12)
                .processingFeeType(LoanFeeType.PERCENTAGE).processingFeeValue(new BigDecimal("2")).guarantorsRequired(1)
                .isActive(true).updatedAt(LocalDateTime.of(2026, 11, 1, 9, 0)).build();
        when(productRepo.findById(7)).thenReturn(Optional.of(product));

        service.update(7, request(), "900001");

        verifyNoInteractions(auditLogService);
    }

    @Test
    void switchingAProductOffIsAudited() {
        LoanProduct product = LoanProduct.builder().id(7).name("Susu Loan").baseInterestRate(new BigDecimal("0.1250"))
                .minAmount(new BigDecimal("100")).maxAmount(new BigDecimal("5000")).maxTermMonths(12)
                .processingFeeType(LoanFeeType.PERCENTAGE).processingFeeValue(new BigDecimal("2")).guarantorsRequired(1)
                .isActive(true).build();
        when(productRepo.findById(7)).thenReturn(Optional.of(product));
        LoanProductRequest request = request();
        request.setActive(false);

        LoanProductResponse response = service.update(7, request, "900001");

        assertThat(response.active()).isFalse();
        verify(auditLogService).recordAs("900001", AuditAction.LOAN_PRODUCT_UPDATED, AuditTargetType.LOAN_PRODUCT, "7",
                "active true -> false");
    }
}
