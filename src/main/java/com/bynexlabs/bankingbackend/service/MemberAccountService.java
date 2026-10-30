package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@RequiredArgsConstructor
@Service
public class MemberAccountService {

    private final MemberAccountRepo memberAccountRepo;

    private final MemberRepository memberRepository;

    public void activateMemberAccounts(Member member) {
        member.setMembershipStatus(MembershipStatus.ACTIVE);

        List<MemberAccount> accounts = memberAccountRepo.findByMember_Id(member.getId());
        if (accounts == null || accounts.isEmpty()) {
            System.err.println("Member accounts not found");
            return;
        }

        for (MemberAccount memberAccount : accounts) {
            memberAccount.setStatus(AccountStatus.ACTIVE);
        }
        memberAccountRepo.saveAll(accounts);
        memberRepository.save(member);
    }
}
