package com.bynexlabs.bankingbackend.DTO.member;

import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.enums.VerificationMethod;
import com.bynexlabs.bankingbackend.models.MemberKycRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A KYC record as shown to reviewers. The document image itself is fetched separately
 * (GET /api/member/v1/kyc-document/{memberId}); its storage key is never exposed.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KycRecordResponse {

    private UUID memberId;
    private String firstName;
    private String lastName;
    private KycDocumentType documentType;
    private String idNumber;
    private KycStatus status;
    // Why the record is in MANUAL_REVIEW (automated check) or REJECTED (administrator).
    private String reason;
    private VerificationMethod verificationMethod;
    private String reviewedByStaffId;
    private String registeredByStaffId;
    private LocalDateTime submittedAt;
    private LocalDateTime verifiedAt;

    /** Must be called inside a transaction: member and staff are loaded lazily. */
    public static KycRecordResponse from(MemberKycRecord record) {
        return KycRecordResponse.builder()
                .memberId(record.getMember().getId())
                .firstName(record.getMember().getFirstName())
                .lastName(record.getMember().getLastName())
                .documentType(record.getDocumentType())
                .idNumber(record.getIdNumber())
                .status(record.getStatus())
                .reason(record.getRejectionReason())
                .verificationMethod(record.getVerificationMethod())
                .reviewedByStaffId(record.getStaff() == null ? null : record.getStaff().getStaffId())
                .registeredByStaffId(record.getMember().getRegisteredBy() == null
                        ? null : record.getMember().getRegisteredBy().getStaffId())
                .submittedAt(record.getCreatedAt())
                .verifiedAt(record.getVerifiedAt())
                .build();
    }
}
