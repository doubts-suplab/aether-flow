package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlaPolicyTest {

    @Test
    void defaultForHasBudgetAndNoChain() {
        var policy = SlaPolicy.defaultFor("tenant-1");
        assertThat(policy.defaultSlaMinutes()).isEqualTo(SlaPolicy.DEFAULT_SLA_MINUTES);
        assertThat(policy.escalationChain()).isEmpty();
        assertThat(policy.hasNextLevel(0)).isFalse();
        assertThat(policy.roleAtLevel(0)).isEmpty();
    }

    @Test
    void chainDrivesNextLevelLookup() {
        var policy = new SlaPolicy("tenant-1", 30, List.of("lead", "manager", "executive"));
        assertThat(policy.hasNextLevel(0)).isTrue();
        assertThat(policy.roleAtLevel(0)).contains("lead");
        assertThat(policy.roleAtLevel(2)).contains("executive");
        assertThat(policy.hasNextLevel(3)).isFalse();   // exhausted
        assertThat(policy.roleAtLevel(3)).isEmpty();
    }

    @Test
    void cleansBlankAndNullChainEntries() {
        var policy = new SlaPolicy("tenant-1", 30, Arrays.asList("lead", " ", null, "  manager "));
        assertThat(policy.escalationChain()).containsExactly("lead", "manager");
    }

    @Test
    void rejectsBlankTenantAndNegativeBudget() {
        assertThatThrownBy(() -> new SlaPolicy(" ", 30, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tenantId");
        assertThatThrownBy(() -> new SlaPolicy("t", -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("defaultSlaMinutes");
    }

    @Test
    void nullChainBecomesEmpty() {
        assertThat(new SlaPolicy("t", 30, null).escalationChain()).isEmpty();
    }

    @Test
    void defaultPolicyHasNoBusinessHours_andUsesWallClock() {
        var policy = SlaPolicy.defaultFor("t");
        assertThat(policy.hasBusinessHours()).isFalse();
        var start = LocalDateTime.of(2026, 8, 19, 10, 0).toInstant(ZoneOffset.UTC);
        // No calendar → plain wall-clock: start + 30 minutes.
        assertThat(policy.deadlineFrom(start, 30)).isEqualTo(start.plusSeconds(1800));
    }

    @Test
    void withBusinessHours_measuresWorkingTime() {
        var policy = new SlaPolicy("t", 60, List.of())
                .withBusinessHours(BusinessHours.standard(ZoneOffset.UTC));
        assertThat(policy.hasBusinessHours()).isTrue();
        // Fri 16:30 + 60 working minutes spills across the weekend to Mon 09:30.
        var friEvening = LocalDateTime.of(2026, 8, 21, 16, 30).toInstant(ZoneOffset.UTC);
        var expected = LocalDateTime.of(2026, 8, 24, 9, 30).toInstant(ZoneOffset.UTC);
        assertThat(policy.deadlineFrom(friEvening, 60)).isEqualTo(expected);
    }

    @Test
    void negativeBudget_isClampedToZero() {
        var start = LocalDateTime.of(2026, 8, 19, 10, 0).toInstant(ZoneOffset.UTC);
        assertThat(SlaPolicy.defaultFor("t").deadlineFrom(start, -5)).isEqualTo(start);
    }
}
