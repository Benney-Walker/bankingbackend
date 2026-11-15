package com.bynexlabs.bankingbackend.service;

import com.bynexlabs.bankingbackend.Repository.RefreshTokenRepo;
import com.bynexlabs.bankingbackend.Repository.StaffRepo;
import com.bynexlabs.bankingbackend.enums.Roles;
import com.bynexlabs.bankingbackend.enums.SessionEndReason;
import com.bynexlabs.bankingbackend.enums.StaffStatus;
import com.bynexlabs.bankingbackend.exception.UnauthorizedException;
import com.bynexlabs.bankingbackend.models.RefreshToken;
import com.bynexlabs.bankingbackend.models.Staff;
import com.bynexlabs.bankingbackend.models.StaffRoles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-15T16:30:00.700Z");
    private static final Instant CUTOFF = Instant.parse("2026-11-15T16:30:00Z");

    @Mock
    private RefreshTokenRepo refreshTokenRepo;

    @Mock
    private StaffRepo staffRepo;

    private SessionService service;

    private final Staff collector = staff("900110", StaffStatus.ACTIVE, Roles.COLLECTOR);

    @BeforeEach
    void setUp() {
        service = new SessionService(refreshTokenRepo, staffRepo);
        service.refreshExpirationInMs = 24 * 60 * 60 * 1000L;
        service.refreshRetryGraceSeconds = 30;
        service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    private static Staff staff(String staffId, StaffStatus status, Roles role) {
        Staff staff = Staff.builder().id(UUID.randomUUID()).staffId(staffId).staffStatus(status).build();
        StaffRoles row = new StaffRoles();
        row.setStaff(staff);
        row.setRole(role);
        staff.setRoles(List.of(row));
        return staff;
    }

    private RefreshToken token(String raw, Instant replacedAt, SessionEndReason revoked) {
        RefreshToken token = RefreshToken.builder().id(UUID.randomUUID()).staff(collector).familyId(UUID.randomUUID())
                .tokenHash(SessionService.hash(raw)).issuedAt(NOW.minusSeconds(3600))
                .expiresAt(NOW.plusSeconds(3600)).build();
        token.setReplacedAt(replacedAt);
        if (revoked != null) {
            token.setRevokedAt(NOW.minusSeconds(60));
            token.setRevokeReason(revoked);
        }
        when(refreshTokenRepo.lockByTokenHash(SessionService.hash(raw))).thenReturn(Optional.of(token));
        return token;
    }

    private RefreshToken savedToken() {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepo).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(SessionService.hash("abc")).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void loginStartsASessionAndStoresOnlyTheHash() {
        when(staffRepo.getReferenceById(collector.getId())).thenReturn(collector);

        String raw = service.startSession(collector);

        RefreshToken saved = savedToken();
        assertThat(raw).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getTokenHash()).isEqualTo(SessionService.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getParentId()).isNull();
        assertThat(saved.getExpiresAt()).isEqualTo(NOW.plusSeconds(24 * 3600));
    }

    @Test
    void refreshSwapsTheTokenKeepingFamilyAndExpiry() {
        RefreshToken old = token("old", null, null);

        SessionService.Rotation rotation = service.rotate("old", roles -> true);

        assertThat(old.getReplacedAt()).isEqualTo(NOW);
        RefreshToken next = savedToken();
        assertThat(next.getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(next.getParentId()).isEqualTo(old.getId());
        assertThat(next.getExpiresAt()).isEqualTo(old.getExpiresAt());
        assertThat(next.getTokenHash()).isEqualTo(SessionService.hash(rotation.refreshToken()));
        assertThat(rotation.staffId()).isEqualTo("900110");
        assertThat(rotation.roles()).containsExactly(Roles.COLLECTOR);
    }

    @Test
    void unknownTokenIsRefused() {
        when(refreshTokenRepo.lockByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope", roles -> true)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepo, never()).save(any());
    }

    @Test
    void expiredTokenIsRefused() {
        RefreshToken old = RefreshToken.builder().id(UUID.randomUUID()).staff(collector).familyId(UUID.randomUUID())
                .tokenHash(SessionService.hash("old")).issuedAt(NOW.minusSeconds(90000)).expiresAt(NOW).build();
        when(refreshTokenRepo.lockByTokenHash(SessionService.hash("old"))).thenReturn(Optional.of(old));

        assertThatThrownBy(() -> service.rotate("old", roles -> true)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepo, never()).save(any());
    }

    @Test
    void loggedOutTokenIsRefusedWithoutEndingAnythingElse() {
        token("old", null, SessionEndReason.LOGOUT);

        assertThatThrownBy(() -> service.rotate("old", roles -> true)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepo, never()).revokeFamily(any(), any(), any());
    }

    // A swapped token used again, outside the retry grace: someone has a copy.
    @Test
    void reusedTokenEndsTheWholeSession() {
        RefreshToken old = token("old", NOW.minusSeconds(120), null);

        assertThatThrownBy(() -> service.rotate("old", roles -> true)).isInstanceOf(UnauthorizedException.class);

        verify(refreshTokenRepo).revokeFamily(old.getFamilyId(), SessionEndReason.REUSE_DETECTED, NOW);
        verify(staffRepo).cutSessions(collector.getId(), CUTOFF);
        verify(refreshTokenRepo, never()).save(any());
    }

    // The app retried a refresh whose response it never got: the unused newer token is replaced.
    @Test
    void retriedRefreshWithinGraceReplacesTheUnusedNewerToken() {
        RefreshToken old = token("old", NOW.minusSeconds(5), null);
        RefreshToken newer = RefreshToken.builder().id(UUID.randomUUID()).staff(collector).familyId(old.getFamilyId())
                .parentId(old.getId()).tokenHash("x").issuedAt(NOW.minusSeconds(5)).expiresAt(old.getExpiresAt()).build();
        when(refreshTokenRepo.findFirstByParentIdOrderByIssuedAtDesc(old.getId())).thenReturn(Optional.of(newer));

        service.rotate("old", roles -> true);

        assertThat(newer.getRevokeReason()).isEqualTo(SessionEndReason.SUPERSEDED);
        assertThat(savedToken().getParentId()).isEqualTo(old.getId());
        verify(refreshTokenRepo, never()).revokeFamily(any(), any(), any());
    }

    @Test
    void retryAfterTheNewerTokenWasUsedIsReuse() {
        RefreshToken old = token("old", NOW.minusSeconds(5), null);
        RefreshToken newer = RefreshToken.builder().id(UUID.randomUUID()).staff(collector).familyId(old.getFamilyId())
                .parentId(old.getId()).tokenHash("x").issuedAt(NOW.minusSeconds(5)).expiresAt(old.getExpiresAt()).build();
        newer.setReplacedAt(NOW.minusSeconds(2));
        when(refreshTokenRepo.findFirstByParentIdOrderByIssuedAtDesc(old.getId())).thenReturn(Optional.of(newer));

        assertThatThrownBy(() -> service.rotate("old", roles -> true)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepo).revokeFamily(old.getFamilyId(), SessionEndReason.REUSE_DETECTED, NOW);
    }

    // A token replaced on a retry never reached the app, so presenting it means a copy.
    @Test
    void supersededTokenEndsTheSession() {
        RefreshToken superseded = token("lost", null, SessionEndReason.SUPERSEDED);

        assertThatThrownBy(() -> service.rotate("lost", roles -> true)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepo).revokeFamily(superseded.getFamilyId(), SessionEndReason.REUSE_DETECTED, NOW);
    }

    @Test
    void wrongEndpointIsRefusedWithoutSwappingTheToken() {
        RefreshToken old = token("old", null, null);

        assertThatThrownBy(() -> service.rotate("old", roles -> !roles.contains(Roles.COLLECTOR)))
                .isInstanceOf(UnauthorizedException.class);
        assertThat(old.getReplacedAt()).isNull();
        verify(refreshTokenRepo, never()).save(any());
    }

    @Test
    void inactiveStaffCantRefresh() {
        collector.setStaffStatus(StaffStatus.INACTIVE);
        token("old", null, null);

        assertThatThrownBy(() -> service.rotate("old", roles -> true)).isInstanceOf(UnauthorizedException.class);
        verify(refreshTokenRepo, never()).save(any());
    }

    @Test
    void logoutEndsThatSessionAndCutsAccessTokens() {
        RefreshToken current = token("current", null, null);

        service.endSession("current");

        verify(refreshTokenRepo).revokeFamily(current.getFamilyId(), SessionEndReason.LOGOUT, NOW);
        verify(staffRepo).cutSessions(collector.getId(), CUTOFF);
    }

    @Test
    void logoutWithAnUnknownTokenDoesNothing() {
        when(refreshTokenRepo.lockByTokenHash(anyString())).thenReturn(Optional.empty());

        service.endSession("unknown");

        verify(refreshTokenRepo, never()).revokeFamily(any(), any(), any());
        verify(staffRepo, never()).cutSessions(any(), any());
    }

    @Test
    void endAllSessionsRevokesEveryTokenAndCutsAccessTokens() {
        service.endAllSessions(collector.getId(), SessionEndReason.DAILY_APPROVAL);

        verify(refreshTokenRepo).revokeAllForStaff(collector.getId(), SessionEndReason.DAILY_APPROVAL, NOW);
        verify(staffRepo).cutSessions(collector.getId(), CUTOFF);
    }
}
