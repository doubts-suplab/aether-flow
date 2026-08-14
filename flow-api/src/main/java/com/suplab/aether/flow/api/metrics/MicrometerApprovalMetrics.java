package com.suplab.aether.flow.api.metrics;

import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalMetricsPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer-backed {@link ApprovalMetricsPort} — publishes operator counters over the human review
 * lifecycle.
 *
 * <p>Registers four monotonic counters so an operator dashboard can watch review-queue throughput:</p>
 * <ul>
 *   <li>{@code aether.flow.approvals.raised} — tasks raised at a human gate (or from a Grid deferral)</li>
 *   <li>{@code aether.flow.approvals.approved} — tasks approved by a human</li>
 *   <li>{@code aether.flow.approvals.rejected} — tasks rejected by a human</li>
 *   <li>{@code aether.flow.approvals.reassigned} — open tasks delegated to another role</li>
 * </ul>
 *
 * <p>Escalation and the open-queue depth are metered by the escalation sweep
 * ({@code aether.flow.escalation.escalated} and the {@code aether.flow.approvals.open} gauge), so this
 * adapter does not duplicate them. Together they form the full operator metric set.</p>
 */
public class MicrometerApprovalMetrics implements ApprovalMetricsPort {

    private final Counter raised;
    private final Counter approved;
    private final Counter rejected;
    private final Counter reassigned;

    public MicrometerApprovalMetrics(MeterRegistry meterRegistry) {
        this.raised = Counter.builder("aether.flow.approvals.raised")
                .description("Total approval tasks raised at a human gate").register(meterRegistry);
        this.approved = Counter.builder("aether.flow.approvals.approved")
                .description("Total approval tasks approved by a human").register(meterRegistry);
        this.rejected = Counter.builder("aether.flow.approvals.rejected")
                .description("Total approval tasks rejected by a human").register(meterRegistry);
        this.reassigned = Counter.builder("aether.flow.approvals.reassigned")
                .description("Total approval tasks reassigned (delegated) to another role")
                .register(meterRegistry);
    }

    @Override
    public void recordRaised(ApprovalTask task) {
        raised.increment();
    }

    @Override
    public void recordApproved(ApprovalTask task) {
        approved.increment();
    }

    @Override
    public void recordRejected(ApprovalTask task) {
        rejected.increment();
    }

    @Override
    public void recordReassigned(ApprovalTask task) {
        reassigned.increment();
    }
}
