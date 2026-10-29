package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AsyncService {

    private final MemberService memberService;

    private final IdGenerator idGenerator;

    private final MemberAccountRepo memberAccountRepo;

    private final MemberKycRecordRepo kycRecordRepo;

    private final KycService kycService;

    @Transactional
    public void createDefaultSharesAccount(SharesAccountEvent sharesAccountEvent) {
        Member member = memberService.getMemberDetails(sharesAccountEvent.getMemberId());
        if (member == null) {
            throw new RuntimeException("Error: member not found");
        }

        MemberAccount account = new MemberAccount();
        account.setAccountNumber(idGenerator.generateId(Id_Type.MEMBER_ACCOUNT));
        account.setMember(member);
        account.setAccountType(AccountType.SHARES);
        account.setStatus(AccountStatus.INACTIVE);
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setCreatedAt(LocalDateTime.now());
        memberAccountRepo.save(account);
    }

    @Transactional
    public void createOtherAccount(OtherAccountCreationEvent otherEvent) {
        Member member = memberService.getMemberDetails(otherEvent.getMemberId());
        if (member == null) {
            throw new RuntimeException("Error: member not found");
        }

        MemberAccount account = new MemberAccount();
        account.setAccountNumber(idGenerator.generateId(Id_Type.MEMBER_ACCOUNT));
        account.setMember(member);
        account.setStatus(AccountStatus.INACTIVE);
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setCreatedAt(LocalDateTime.now());
        if (otherEvent.getAccountType() == AccountType.SAVINGS) {
            account.setAccountType(AccountType.SAVINGS);
        } else {
            account.setAccountType(AccountType.CURRENT);
        }
        memberAccountRepo.save(account);
    }

    public void processKycVerification(KycVerificationEvent kycEvent) {

        MemberKycRecord record = kycRecordRepo.findById(kycEvent.getKycRecordId())
                .orElseThrow(() -> new IllegalArgumentException("KYC Record not found"));

        Member member = record.getMember();

        try {

            String objectKey = kycService.extractKeyFromUrl(record.getDocumentUrl());
            byte[] documentBytes = kycService.downloadDocumentFromR2(objectKey);

            Map<String, IdentityDocumentField> extractedData = kycService.extractIdInformation(documentBytes);

            kycService.verifyAndRoute(record, member, extractedData);

        } catch (Exception e) {
            System.out.println("KYC processing failed for record " + e.getMessage());
            record.setStatus(KycStatus.MANUAL_REVIEW);
            record.setRejectionReason("System error during automated OCR extraction: " + e.getMessage());
            kycRecordRepo.save(record);
        }
    }
}
