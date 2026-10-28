package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.DTO.rabbitMQ.ShareAccountEvent;
import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Utility.IdGenerator;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.Id_Type;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class AsyncService {

    private final MemberService memberService;

    private final IdGenerator idGenerator;

    private final MemberAccountRepo memberAccountRepo;

    public void createDefaultSharesAccount(ShareAccountEvent shareAccountEvent) {
        Member member = memberService.getMemberDetails(shareAccountEvent.getMemberId());
        if (member == null) {
            throw new RuntimeException("Error: member not found");
        }

        MemberAccount account = new MemberAccount();
        account.setAccountNumber(idGenerator.generateId(Id_Type.MEMBER_ACCOUNT));
        account.setMember(member);
        account.setAccountType(AccountType.SHARES);
        account.setStatus(AccountStatus.ACTIVE);
        account.setCurrentBalance(BigDecimal.ZERO);
        account.setAvailableBalance(BigDecimal.ZERO);
        account.setCreatedAt(LocalDateTime.now());
        memberAccountRepo.save(account);
    }
}
