package com.bynexlabs.bankingbackend.DTO.member;

import com.bynexlabs.bankingbackend.enums.AccountStatus;
import com.bynexlabs.bankingbackend.enums.AccountType;
import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import com.bynexlabs.bankingbackend.models.MemberAccount;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A member's details with their accounts. Balances are deliberately left out: who may see
 * balances belongs with balance enquiry (roadmap 5.9) and hasn't been decided.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MemberProfileResponse {

    private UUID memberId;
    private String firstName;
    private String middleName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String residentialAddress;
    private String occupation;
    private MembershipStatus membershipStatus;
    // Staff ID of whoever registered the member; null for members registered before V20.
    private String registeredByStaffId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<AccountSummary> accounts;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AccountSummary {
        private String accountNumber;
        private AccountType accountType;
        private AccountStatus status;
    }

    public static MemberProfileResponse from(Member member, List<MemberAccount> accounts) {
        return MemberProfileResponse.builder()
                .memberId(member.getId())
                .firstName(member.getFirstName())
                .middleName(member.getMiddleName())
                .lastName(member.getLastName())
                .dateOfBirth(member.getDateOfBirth())
                .phoneNumber(member.getPhoneNumber())
                .residentialAddress(member.getResidentialAddress())
                .occupation(member.getOccupation())
                .membershipStatus(member.getMembershipStatus())
                .registeredByStaffId(member.getRegisteredBy() == null ? null : member.getRegisteredBy().getStaffId())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .accounts(accounts.stream()
                        .map(a -> new AccountSummary(a.getAccountNumber(), a.getAccountType(), a.getStatus()))
                        .toList())
                .build();
    }
}
