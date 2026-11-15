package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.RefreshTokenRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.SessionEndReason;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.models.RefreshToken;
import com.bynexlabs.bankingbackend.models.Staff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Staff sessions (owner decisions 2026-11-15). Refresh tokens are random strings kept on the
 * server as SHA-256 hashes, so they can be revoked:
 * - login starts a session (a token family); each refresh swaps the token for a new one with the
 *   same expiry, and the old one stops working;
 * - using a swapped token again means someone has a copy, so the whole session ends, unless it is
 *   the app retrying within a few seconds a refresh whose response it never got and the new token
 *   hasn't been used yet: then that new token is replaced instead;
 * - logout ends the session on that device; daily approval, password reset, deactivation and role
 *   changes end all of the staff member's sessions;
 * - ending a session also sets staffs.sessions_valid_after, so access tokens issued before it are
 *   refused straight away (JwtAuthenticationFilter) instead of working until they expire. Other
 *   devices whose sessions are still valid simply refresh.
 *
 * The apps must send one refresh at a time and store the new refresh token from each response.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVALID = "Invalid or expired refresh token";

    // Expired tokens are kept this long for investigation, then deleted.
    private static final Duration KEEP_EXPIRED = Duration.ofDays(30);

    private final RefreshTokenRepo refreshTokenRepo;

    private final StaffRepo staffRepo;

    @Value("${jwt.refresh-expiration}")
    long refreshExpirationInMs;

    @Value("${app.auth.refresh-retry-grace-seconds:30}")
    long refreshRetryGraceSeconds;

    /** Replaceable in tests. */
    Clock clock = Clock.systemUTC();

    /** A new refresh token and the staff member it belongs to, for issuing the access token. */
    public record Rotation(String staffId, List<Roles> roles, String refreshToken) {
    }

    /** Starts a session at login and returns its first refresh token. */
    @Transactional
    public String startSession(Staff staff) {
        Instant now = clock.instant();
        return issue(staffRepo.getReferenceById(staff.getId()), UUID.randomUUID(), null,
                now.plusMillis(refreshExpirationInMs), now);
    }

    /**
     * Swaps a refresh token for a new one. {@code allowed} decides whether this staff member may
     * refresh on this endpoint (collectors only on the collector one); a refusal changes nothing.
     * Revocations made here are kept even though the call fails with 401.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public Rotation rotate(String rawToken, Predicate<List<Roles>> allowed) {
        RefreshToken token = refreshTokenRepo.lockByTokenHash(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException(INVALID));
        Instant now = clock.instant();

        if (token.getRevokedAt() != null) {
            // A token replaced on a retried refresh never reached the app, so its use means a copy.
            if (token.getRevokeReason() == SessionEndReason.SUPERSEDED) {
                endFamily(token, SessionEndReason.REUSE_DETECTED, now);
            }
            throw new UnauthorizedException(INVALID);
        }
        if (!now.isBefore(token.getExpiresAt())) {
            throw new UnauthorizedException(INVALID);
        }
        if (token.getReplacedAt() != null) {
            RefreshToken newer = refreshTokenRepo.findFirstByParentIdOrderByIssuedAtDesc(token.getId()).orElse(null);
            boolean retriedRefresh = newer != null
                    && newer.getReplacedAt() == null && newer.getRevokedAt() == null
                    && !now.isAfter(token.getReplacedAt().plusSeconds(refreshRetryGraceSeconds));
            if (!retriedRefresh) {
                endFamily(token, SessionEndReason.REUSE_DETECTED, now);
                throw new UnauthorizedException(INVALID);
            }
            newer.setRevokedAt(now);
            newer.setRevokeReason(SessionEndReason.SUPERSEDED);
        }

        Staff staff = token.getStaff();
        List<Roles> roles = staff.getRoles() == null ? List.of()
                : staff.getRoles().stream().map(role -> role.getRole()).toList();
        if (staff.getStaffStatus() != StaffStatus.ACTIVE || !allowed.test(roles)) {
            throw new UnauthorizedException(INVALID);
        }

        token.setReplacedAt(now);
        String next = issue(staff, token.getFamilyId(), token.getId(), token.getExpiresAt(), now);
        return new Rotation(staff.getStaffId(), roles, next);
    }

    /** Logout: ends the session the token belongs to. Unknown or used-up tokens are ignored. */
    @Transactional
    public void endSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepo.lockByTokenHash(hash(rawToken))
                .filter(token -> token.getRevokedAt() == null)
                .ifPresent(token -> endFamily(token, SessionEndReason.LOGOUT, clock.instant()));
    }

    /** Ends every session of a staff member, on every device. */
    @Transactional
    public void endAllSessions(UUID staffId, SessionEndReason reason) {
        Instant now = clock.instant();
        int revoked = refreshTokenRepo.revokeAllForStaff(staffId, reason, now);
        staffRepo.cutSessions(staffId, cutoff(now));
        log.info("Ended all sessions of staff {} ({}): {} refresh tokens revoked", staffId, reason, revoked);
    }

    /** Daily housekeeping: expired tokens are deleted after 30 days. */
    @Scheduled(cron = "${app.auth.refresh-token-cleanup-cron:0 30 3 * * *}", zone = "Africa/Accra")
    @Transactional
    public void deleteOldTokens() {
        int deleted = refreshTokenRepo.deleteExpiredBefore(clock.instant().minus(KEEP_EXPIRED));
        if (deleted > 0) {
            log.info("Deleted {} expired refresh tokens", deleted);
        }
    }

    private void endFamily(RefreshToken token, SessionEndReason reason, Instant now) {
        refreshTokenRepo.revokeFamily(token.getFamilyId(), reason, now);
        staffRepo.cutSessions(token.getStaff().getId(), cutoff(now));
        if (reason == SessionEndReason.REUSE_DETECTED) {
            log.warn("Refresh token reuse for staff {}: session {} ended", token.getStaff().getStaffId(), token.getFamilyId());
        }
    }

    private String issue(Staff staff, UUID familyId, UUID parentId, Instant expiresAt, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepo.save(RefreshToken.builder()
                .id(UUID.randomUUID())
                .staff(staff)
                .familyId(familyId)
                .parentId(parentId)
                .tokenHash(hash(raw))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build());
        return raw;
    }

    // JWT issued-at times are whole seconds, so the cut-off is too: access tokens from earlier
    // seconds are refused.
    private static Instant cutoff(Instant now) {
        return now.truncatedTo(ChronoUnit.SECONDS);
    }

    static String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
