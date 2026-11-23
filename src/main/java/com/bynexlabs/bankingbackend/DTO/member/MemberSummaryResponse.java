package com.bynexlabs.bankingbackend.DTO.member;

import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * One row in member search results (owner decisions 2026-11-23). Every row shows the member's
 * name, branch, membership status and accounts (number, type, status). {@code phoneNumber} is
 * filled for administrators and for agents searching their own branch; for a member of another
 * branch an agent gets null. Balances are never included.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberSummaryResponse {

    private UUID memberId;
    private String firstName;
    private String middleName;
    private String lastName;
    private String phoneNumber;
    private UUID branchId;
    private String branchName;
    private MembershipStatus membershipStatus;
    private List<MemberProfileResponse.AccountSummary> accounts;

    /** Must run inside a transaction: reads the member's branch. */
    public static MemberSummaryResponse from(Member member, List<MemberAccount> accounts, boolean showPhone) {
        return MemberSummaryResponse.builder()
                .memberId(member.getId())
                .firstName(member.getFirstName())
                .middleName(member.getMiddleName())
                .lastName(member.getLastName())
                .phoneNumber(showPhone ? member.getPhoneNumber() : null)
                .branchId(member.getBranch() == null ? null : member.getBranch().getId())
                .branchName(member.getBranch() == null ? null : member.getBranch().getName())
                .membershipStatus(member.getMembershipStatus())
                .accounts(accounts.stream()
                        .map(a -> new MemberProfileResponse.AccountSummary(a.getAccountNumber(), a.getAccountType(), a.getStatus()))
                        .toList())
                .build();
    }
}
