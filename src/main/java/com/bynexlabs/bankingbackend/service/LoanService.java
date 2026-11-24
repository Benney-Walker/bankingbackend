package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.loan.LoanProductResponse;
import com.bynexlabs.bankingbackend.DTO.loan.LoanResponse;
import com.bynexlabs.bankingbackend.Repository.AmortizationScheduleRepo;
import com.bynexlabs.bankingbackend.Repository.LoanAccountRepo;
import com.bynexlabs.bankingbackend.enums.ScheduleStatus;
import com.bynexlabs.bankingbackend.exception.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.models.AmortizationSchedule;
import com.bynexlabs.bankingbackend.models.LoanAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Reading loans: a member's loans, and one loan with its installments. Any branch, admins and agents. */
@Service
@RequiredArgsConstructor
public class LoanService {

    private final LoanAccountRepo loanAccountRepo;

    private final AmortizationScheduleRepo scheduleRepo;

    @Transactional(readOnly = true)
    public List<LoanResponse> forMember(UUID memberId) {
        return loanAccountRepo.findByMember_IdOrderByCreatedAtDesc(memberId).stream()
                .map(loan -> response(loan, scheduleRepo.findByLoanAccount_IdOrderByInstallmentNumberAsc(loan.getId()), false))
                .toList();
    }

    @Transactional(readOnly = true)
    public LoanResponse get(UUID loanId) {
        LoanAccount loan = loanAccountRepo.findWithDetailsById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));
        return response(loan, scheduleRepo.findByLoanAccount_IdOrderByInstallmentNumberAsc(loanId), true);
    }

    static LoanResponse response(LoanAccount loan, List<AmortizationSchedule> installments, boolean withInstallments) {
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        for (AmortizationSchedule installment : installments) {
            paid = paid.add(installment.paidTotal());
            outstanding = outstanding.add(installment.remaining());
            if (installment.getStatus() == ScheduleStatus.OVERDUE) {
                overdue = overdue.add(installment.remaining());
            }
        }
        var member = loan.getMember();
        return new LoanResponse(loan.getId(), loan.getLoanApplication().getId(), member.getId(),
                member.getFirstName() + " " + member.getLastName(), loan.getMemberAccount().getAccountNumber(),
                loan.getLoanApplication().getLoanProduct().getName(), loan.getPrincipalAmount(),
                LoanProductResponse.percent(loan.getCurrentInterestRate()), loan.getTotalInterest(),
                loan.getPrincipalAmount().add(loan.getTotalInterest()), loan.getProcessingFee(), loan.getTermMonths(),
                loan.getDisbursementDate(), loan.getMaturityDate(), loan.getStatus(), paid, outstanding, overdue,
                withInstallments ? installments.stream()
                        .map(i -> new LoanResponse.Installment(i.getInstallmentNumber(), i.getDueDate(), i.getExpectedTotal(),
                                i.paidTotal(), i.remaining(), i.getStatus()))
                        .toList() : null);
    }
}
