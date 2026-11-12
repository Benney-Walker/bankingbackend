package com.bynexlabs.bankingbackend.DTO.member;

import com.bynexlabs.bankingbackend.enums.MembershipStatus;
import com.bynexlabs.bankingbackend.models.Member;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** One row in member search results. */
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
    private MembershipStatus membershipStatus;

    public static MemberSummaryResponse from(Member member) {
        return MemberSummaryResponse.builder()
                .memberId(member.getId())
                .firstName(member.getFirstName())
                .middleName(member.getMiddleName())
                .lastName(member.getLastName())
                .phoneNumber(member.getPhoneNumber())
                .membershipStatus(member.getMembershipStatus())
                .build();
    }
}
