package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.MemberAccountRepo;
import com.bynexlabs.bankingbackend.Utility.BadRequestException;
import com.bynexlabs.bankingbackend.Utility.ConflictException;
import com.bynexlabs.bankingbackend.Utility.ResourceNotFoundException;
import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountLockServiceTest {

    // Fixed IDs so the lock order is known: LOW sorts before HIGH.
    private static final UUID LOW = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID HIGH = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private MemberAccountRepo memberAccountRepo;

    @InjectMocks
    private AccountLockService accountLockService;

    private MemberAccount account(UUID id, String number, AccountStatus status, MembershipStatus memberStatus) {
        Member member = Member.builder().id(UUID.randomUUID()).membershipStatus(memberStatus).build();
        return MemberAccount.builder().id(id).accountNumber(number).status(status).member(member).build();
    }

    private void stubAccount(MemberAccount account) {
        when(memberAccountRepo.findIdByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account.getId()));
        when(memberAccountRepo.lockById(account.getId())).thenReturn(Optional.of(account));
    }

    @Test
    void locksAnActiveAccountOfAnActiveMember() {
        MemberAccount account = account(LOW, "1000000001", AccountStatus.ACTIVE, MembershipStatus.ACTIVE);
        stubAccount(account);

        assertThat(accountLockService.lockForTransaction(" 1000000001 ")).isSameAs(account);
        verify(memberAccountRepo).lockById(LOW);
    }

    @Test
    void unknownAccountIsNotFound() {
        when(memberAccountRepo.findIdByAccountNumber("1000000009")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountLockService.lockForTransaction("1000000009"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found");
        verify(memberAccountRepo, never()).lockById(any());
    }

    @Test
    void blankAccountNumberIsRejected() {
        assertThatThrownBy(() -> accountLockService.lockForTransaction(" "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Account number is required");
    }

    @Test
    void accountThatIsNotActiveCannotTransact() {
        stubAccount(account(LOW, "1000000001", AccountStatus.FROZEN, MembershipStatus.ACTIVE));

        assertThatThrownBy(() -> accountLockService.lockForTransaction("1000000001"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Account 1000000001 is FROZEN");
    }

    // Accounts of a member still waiting for KYC verification can't move money.
    @Test
    void accountOfAMemberWhoIsNotActiveCannotTransact() {
        stubAccount(account(LOW, "1000000001", AccountStatus.ACTIVE, MembershipStatus.PENDING));

        assertThatThrownBy(() -> accountLockService.lockForTransaction("1000000001"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("The member who owns account 1000000001 is not active");
    }

    @Test
    void transferLocksTheLowerAccountIdFirstWhicheverWayTheMoneyGoes() {
        MemberAccount low = account(LOW, "1000000001", AccountStatus.ACTIVE, MembershipStatus.ACTIVE);
        MemberAccount high = account(HIGH, "1000000002", AccountStatus.ACTIVE, MembershipStatus.ACTIVE);
        stubAccount(low);
        stubAccount(high);

        List<MemberAccount> locked = accountLockService.lockForTransfer("1000000002", "1000000001");

        assertThat(locked).containsExactly(high, low);
        InOrder order = inOrder(memberAccountRepo);
        order.verify(memberAccountRepo).lockById(LOW);
        order.verify(memberAccountRepo).lockById(HIGH);
    }

    @Test
    void transferToTheSameAccountIsRejectedBeforeLocking() {
        when(memberAccountRepo.findIdByAccountNumber("1000000001")).thenReturn(Optional.of(LOW));

        assertThatThrownBy(() -> accountLockService.lockForTransfer("1000000001", "1000000001"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Cannot transfer to the same account");
        verify(memberAccountRepo, never()).lockById(any());
    }

    @Test
    void transferChecksTheReceivingAccountToo() {
        stubAccount(account(LOW, "1000000001", AccountStatus.ACTIVE, MembershipStatus.ACTIVE));
        stubAccount(account(HIGH, "1000000002", AccountStatus.CLOSED, MembershipStatus.ACTIVE));

        assertThatThrownBy(() -> accountLockService.lockForTransfer("1000000001", "1000000002"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Account 1000000002 is CLOSED");
    }
}
