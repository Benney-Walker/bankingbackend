package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.OtherAccountCreationEvent;
import com.bynexlabs.bankingbackend.DTO.rabbitMQ.SharesAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@RequiredArgsConstructor
@Service
public class AsyncService {

    private final MemberService memberService;

    private final IdGenerator idGenerator;

    private final MemberAccountRepo memberAccountRepo;

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
}
