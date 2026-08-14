package com.suplab.aether.flow.api.metrics;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerApprovalMetricsTest {

    private static ApprovalTask task() {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), "tenant-1", UUID.randomUUID(), "wf", "review",
                "reviewer", ApprovalOutcome.PENDING, now.plusSeconds(3600), now, null, null, null, 0);
    }

    @Test
    void eachTransitionIncrementsItsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        var metrics = new MicrometerApprovalMetrics(registry);

        metrics.recordRaised(task());
        metrics.recordRaised(task());
        metrics.recordApproved(task());
        metrics.recordRejected(task());
        metrics.recordReassigned(task());

        assertThat(registry.counter("aether.flow.approvals.raised").count()).isEqualTo(2.0);
        assertThat(registry.counter("aether.flow.approvals.approved").count()).isEqualTo(1.0);
        assertThat(registry.counter("aether.flow.approvals.rejected").count()).isEqualTo(1.0);
        assertThat(registry.counter("aether.flow.approvals.reassigned").count()).isEqualTo(1.0);
    }

    @Test
    void countersRegisterEvenBeforeAnyEvent() {
        MeterRegistry registry = new SimpleMeterRegistry();
        new MicrometerApprovalMetrics(registry);

        // all four counters exist at zero, so a dashboard sees them from startup
        assertThat(registry.find("aether.flow.approvals.raised").counter()).isNotNull();
        assertThat(registry.find("aether.flow.approvals.approved").counter()).isNotNull();
        assertThat(registry.find("aether.flow.approvals.rejected").counter()).isNotNull();
        assertThat(registry.find("aether.flow.approvals.reassigned").counter()).isNotNull();
    }
}
