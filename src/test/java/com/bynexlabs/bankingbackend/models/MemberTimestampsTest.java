package com.bynexlabs.bankingbackend.models;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MemberTimestampsTest {

    @Test
    void onCreateStampsBothTimestamps() {
        Member member = new Member();

        member.onCreate();

        assertThat(member.getCreatedAt()).isNotNull();
        assertThat(member.getUpdatedAt()).isEqualTo(member.getCreatedAt());
    }

    @Test
    void onUpdateRefreshesUpdatedAtButKeepsCreatedAt() {
        LocalDateTime registeredAt = LocalDateTime.now().minusDays(3);
        Member member = new Member();
        member.setCreatedAt(registeredAt);
        member.setUpdatedAt(registeredAt);

        member.onUpdate();

        assertThat(member.getCreatedAt()).isEqualTo(registeredAt);
        assertThat(member.getUpdatedAt()).isAfter(registeredAt);
    }
}
