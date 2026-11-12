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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAccountServiceTest {

    @Mock
    private MemberAccountRepo memberAccountRepo;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MemberKycRecordRepo kycRecordRepo;

    @InjectMocks
    private MemberAccountService memberAccountService;

    @Test
    void verifiedKycActivatesMemberRecordAndAccounts() {
        Member member = new Member();
        member.setId(UUID.randomUUID());
        member.setMembershipStatus(MembershipStatus.PENDING);
        MemberKycRecord record = new MemberKycRecord();
        record.setStatus(KycStatus.MANUAL_REVIEW);
        MemberAccount shares = new MemberAccount();
        shares.setStatus(AccountStatus.INACTIVE);
        MemberAccount savings = new MemberAccount();
        savings.setStatus(AccountStatus.INACTIVE);
        Staff staff = new Staff();
        when(memberAccountRepo.findByMember_Id(member.getId())).thenReturn(List.of(shares, savings));

        memberAccountService.activateMemberAccounts(member, record, VerificationMethod.MANUAL, staff);

        assertThat(member.getMembershipStatus()).isEqualTo(MembershipStatus.ACTIVE);
        assertThat(record.getStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(record.getVerifiedAt()).isNotNull();
        assertThat(record.getVerificationMethod()).isEqualTo(VerificationMethod.MANUAL);
        assertThat(record.getStaff()).isSameAs(staff);
        assertThat(shares.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(savings.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(kycRecordRepo).save(record);
        verify(memberAccountRepo).saveAll(List.of(shares, savings));
    }
}
