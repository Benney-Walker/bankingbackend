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
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.LoanProduct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loan products (owner decisions 2026-11-24): administrators create and change them; agents and
 * administrators read them to apply. Interest is flat at a yearly rate. Every change is audited.
 * A change applies to applications approved afterwards; loans already paid out keep their terms.
 */
@Service
@RequiredArgsConstructor
public class LoanProductService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final LoanProductRepo productRepo;

    private final AuditLogService auditLogService;

    /** Replaceable in tests. */
    Clock clock = Clock.system(ZoneId.of("Africa/Accra"));

    /** Administrators see every product; agents only active ones. */
    @Transactional(readOnly = true)
    public List<LoanProductResponse> list(boolean includeInactive) {
        return (includeInactive ? productRepo.findAllByOrderByNameAsc() : productRepo.findByIsActiveTrueOrderByNameAsc())
                .stream().map(LoanProductResponse::from).toList();
    }

    @Transactional
    public LoanProductResponse create(LoanProductRequest request, String adminStaffId) {
        validate(request);
        String name = request.getName().trim();
        if (productRepo.existsByNameIgnoreCase(name)) {
            throw new ConflictException("A loan product called " + name + " already exists");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LoanProduct product = LoanProduct.builder()
                .name(name)
                .baseInterestRate(fraction(request.getInterestRatePercent()))
                .interestType(InterestType.FLAT)
                .minAmount(request.getMinAmount())
                .maxAmount(request.getMaxAmount())
                .maxTermMonths(request.getMaxTermMonths())
                .processingFeeType(request.getProcessingFeeType())
                .processingFeeValue(request.getProcessingFeeValue())
                .guarantorsRequired(request.getGuarantorsRequired())
                .isActive(request.getActive())
                .createdAt(now)
                .updatedAt(now)
                .updatedBy(adminStaffId)
                .build();
        LoanProduct saved = productRepo.save(product);
        auditLogService.recordAs(adminStaffId, AuditAction.LOAN_PRODUCT_CREATED, AuditTargetType.LOAN_PRODUCT,
                String.valueOf(saved.getId()), describe(saved));
        return LoanProductResponse.from(saved);
    }

    /** Replaces the product's terms, including switching it off or on. */
    @Transactional
    public LoanProductResponse update(Integer productId, LoanProductRequest request, String adminStaffId) {
        validate(request);
        LoanProduct product = productRepo.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan product not found"));
        String name = request.getName().trim();
        if (productRepo.existsByNameIgnoreCaseAndIdNot(name, productId)) {
            throw new ConflictException("A loan product called " + name + " already exists");
        }

        BigDecimal rate = fraction(request.getInterestRatePercent());
        List<String> changes = new ArrayList<>();
        track(changes, "name", product.getName(), name);
        track(changes, "interestRatePercent", LoanProductResponse.percent(product.getBaseInterestRate()),
                LoanProductResponse.percent(rate));
        track(changes, "minAmount", product.getMinAmount(), request.getMinAmount());
        track(changes, "maxAmount", product.getMaxAmount(), request.getMaxAmount());
        track(changes, "maxTermMonths", product.getMaxTermMonths(), request.getMaxTermMonths());
        track(changes, "processingFeeType", product.getProcessingFeeType(), request.getProcessingFeeType());
        track(changes, "processingFeeValue", product.getProcessingFeeValue(), request.getProcessingFeeValue());
        track(changes, "guarantorsRequired", product.getGuarantorsRequired(), request.getGuarantorsRequired());
        track(changes, "active", product.getIsActive(), request.getActive());
        if (changes.isEmpty()) {
            return LoanProductResponse.from(product);
        }

        product.setName(name);
        product.setBaseInterestRate(rate);
        product.setMinAmount(request.getMinAmount());
        product.setMaxAmount(request.getMaxAmount());
        product.setMaxTermMonths(request.getMaxTermMonths());
        product.setProcessingFeeType(request.getProcessingFeeType());
        product.setProcessingFeeValue(request.getProcessingFeeValue());
        product.setGuarantorsRequired(request.getGuarantorsRequired());
        product.setIsActive(request.getActive());
        product.setUpdatedAt(LocalDateTime.now(clock));
        product.setUpdatedBy(adminStaffId);
        auditLogService.recordAs(adminStaffId, AuditAction.LOAN_PRODUCT_UPDATED, AuditTargetType.LOAN_PRODUCT,
                String.valueOf(productId), String.join("; ", changes));
        return LoanProductResponse.from(product);
    }

    private static void validate(LoanProductRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Name is required");
        }
        if (request.getMaxAmount().compareTo(request.getMinAmount()) < 0) {
            throw new BadRequestException("The maximum amount can't be less than the minimum amount");
        }
        if (request.getProcessingFeeType() == LoanFeeType.PERCENTAGE && request.getProcessingFeeValue().compareTo(HUNDRED) >= 0) {
            throw new BadRequestException("A percentage processing fee must be below 100");
        }
        if (request.getProcessingFeeType() == LoanFeeType.FLAT && request.getProcessingFeeValue().compareTo(request.getMinAmount()) >= 0) {
            throw new BadRequestException("A flat processing fee must be less than the minimum amount, or nothing would be paid out");
        }
    }

    private static BigDecimal fraction(BigDecimal percent) {
        return percent.movePointLeft(2).setScale(4, java.math.RoundingMode.UNNECESSARY);
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

    private static String describe(LoanProduct product) {
        return product.getName() + ": " + plain(LoanProductResponse.percent(product.getBaseInterestRate())) + "% a year flat, GHS "
                + plain(product.getMinAmount()) + "-" + plain(product.getMaxAmount()) + ", up to " + product.getMaxTermMonths()
                + " months, fee " + product.getProcessingFeeType() + " " + plain(product.getProcessingFeeValue())
                + ", guarantors " + product.getGuarantorsRequired() + (Boolean.TRUE.equals(product.getIsActive()) ? "" : ", inactive");
    }
}
