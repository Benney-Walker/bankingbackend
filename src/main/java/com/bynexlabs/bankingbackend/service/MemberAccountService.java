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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.LocalDateTime;
import java.util.List;

@RequiredArgsConstructor
@Slf4j
@Service
public class MemberAccountService {

    private final MemberAccountRepo memberAccountRepo;

    private final MemberRepository memberRepository;

    private final MemberKycRecordRepo kycRecordRepo;

    /**
     * Marks the KYC record verified and activates the member and their accounts. Runs inside the
     * caller's transaction so all of it commits together. staff is null for automated approval.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void activateMemberAccounts(Member member, MemberKycRecord kycRecord, VerificationMethod method, Staff staff) {
        member.setMembershipStatus(MembershipStatus.ACTIVE);
        memberRepository.save(member);

        kycRecord.setStatus(KycStatus.VERIFIED);
        kycRecord.setVerifiedAt(LocalDateTime.now());
        kycRecord.setVerificationMethod(method);
        kycRecord.setStaff(staff);
        kycRecordRepo.save(kycRecord);

        List<MemberAccount> accounts = memberAccountRepo.findByMember_Id(member.getId());
        if (accounts == null || accounts.isEmpty()) {
            log.warn("KYC verified for member {} but no accounts were found to activate", member.getId());
            return;
        }

        for (MemberAccount memberAccount : accounts) {
            memberAccount.setStatus(AccountStatus.ACTIVE);
        }
        memberAccountRepo.saveAll(accounts);
    }
}
