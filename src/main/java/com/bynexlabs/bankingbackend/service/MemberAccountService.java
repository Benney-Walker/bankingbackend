package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Repository.MemberKycRecordRepo;
import com.bynexlabs.bankingbackend.Repository.MemberRepository;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.enums.VerificationMethod;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Service
public class MemberAccountService {

    private final MemberAccountRepo memberAccountRepo;

    private final MemberRepository memberRepository;

    private final MemberKycRecordRepo kycRecordRepo;

    public void activateMemberAccounts(Member member, VerificationMethod method, Staff staff) {
        member.setMembershipStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        MemberKycRecord kycRecord = kycRecordRepo.findByMember_Id(member.getId()).orElse(null);
        kycRecord.setStatus(KycStatus.VERIFIED);
        kycRecord.setVerifiedAt(LocalDateTime.now());
        kycRecord.setVerificationMethod(method);
        kycRecord.setStaff(staff);
        kycRecordRepo.save(kycRecord);

        List<MemberAccount> accounts = memberAccountRepo.findByMember_Id(member.getId());
        if (accounts == null || accounts.isEmpty()) {
            System.err.println("Member accounts not found");
            return;
        }

        for (MemberAccount memberAccount : accounts) {
            memberAccount.setStatus(AccountStatus.ACTIVE);
        }
        memberAccountRepo.saveAll(accounts);
    }
}
