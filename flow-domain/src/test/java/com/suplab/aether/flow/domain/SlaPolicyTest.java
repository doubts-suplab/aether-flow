package com.suplab.aether.flow.domain;

import org.junit.jupiter.api.Test;

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
}
