package com.bynexlabs.bankingbackend.models;

import com.bynexlabs.bankingbackend.enums.SessionEndReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token kept on the server (V39) so sessions can be ended. Only the SHA-256 hash of
 * the token is stored. Every token from one login shares a family; each refresh swaps the token
 * for a new one in the same family, with the same expiry (the session doesn't grow longer).
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter(AccessLevel.PACKAGE)
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class RefreshToken {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "staff_id", nullable = false, updatable = false)
    private Staff staff;

    @Column(name = "family_id", nullable = false, updatable = false)
    private UUID familyId;

    @Column(name = "parent_id", updatable = false)
    private UUID parentId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    /** The collector's phone this session was started on (V42); null for office staff. */
    @Column(name = "device_id", updatable = false, length = 100)
    private String deviceId;

    @Setter
    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Setter
    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(name = "revoke_reason", length = 30)
    private SessionEndReason revokeReason;
}
