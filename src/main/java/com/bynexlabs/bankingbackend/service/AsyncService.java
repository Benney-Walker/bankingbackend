package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.KycVerificationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.textract.model.IdentityDocumentField;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
@Service
public class AsyncService {

    private final MemberService memberService;

    private final IdGenerator idGenerator;

    private final MemberAccountRepo memberAccountRepo;

    private final StorageService storageService;

    private final KycService kycService;

    private final ProcessedMessageService processedMessageService;

    // messageId is the outbox ID; a redelivered message is skipped instead of creating a
    // second account.
    @Transactional
    public void createDefaultSharesAccount(SharesAccountEvent sharesAccountEvent, String messageId) {
        if (!processedMessageService.markFirstDelivery(messageId, RabbitMQConfig.DEFAULT_SHARES_ACCOUNT_QUEUE_NAME)) {
            return;
        }
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
    public void createOtherAccount(OtherAccountCreationEvent otherEvent, String messageId) {
        if (!processedMessageService.markFirstDelivery(messageId, RabbitMQConfig.OTHER_ACCOUNT_CREATION_QUEUE_NAME)) {
            return;
        }
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

    /**
     * Automated KYC check. Deliberately not transactional: the R2 download and the OCR call
     * happen between two short transactions in KycService, never inside one.
     */
    public void processKycVerification(KycVerificationEvent kycEvent) {
        UUID recordId = kycEvent.getKycRecordId();
        Optional<KycService.VerificationTask> task = kycService.startAutomatedVerification(recordId);
        if (task.isEmpty()) {
            return;
        }

        Map<String, IdentityDocumentField> extractedData;
        try {
            byte[] document = storageService.downloadKycDocument(task.get().documentKey());
            extractedData = kycService.extractIdInformation(document);
        } catch (Exception e) {
            // The document couldn't be read or analysed (storage/OCR outage, unreadable image):
            // a person decides instead of guessing.
            log.warn("Automated KYC could not run for record {}; sending it to manual review", recordId, e);
            kycService.sendToManualReview(recordId,
                    "Automated OCR could not be completed (" + e.getClass().getSimpleName() + ")");
            return;
        }

        // Outside the catch on purpose: a database failure here should retry the message, not
        // label the member's document as unreadable.
        kycService.completeAutomatedVerification(recordId, extractedData);
    }
}
