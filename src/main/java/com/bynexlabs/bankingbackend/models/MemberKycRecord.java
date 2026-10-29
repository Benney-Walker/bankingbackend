package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.KycDocumentType;
import com.bynexlabs.bankingbackend.enums.KycStatus;
import com.bynexlabs.bankingbackend.enums.VerificationMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_kyc_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberKycRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 40)
    private KycDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", length = 50)
    private VerificationMethod verificationMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private Staff staff;

    @Column(name = "verified_at", nullable = false)
    private OffsetDateTime verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private KycStatus status;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "document_url", nullable = false)
    private String documentUrl;
}
