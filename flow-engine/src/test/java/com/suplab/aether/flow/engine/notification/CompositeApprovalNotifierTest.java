package com.suplab.aether.flow.engine.notification;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalNotificationPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CompositeApprovalNotifierTest {

    private static ApprovalTask task() {
        var now = Instant.now();
        return new ApprovalTask(UUID.randomUUID(), "tenant-1", UUID.randomUUID(), "wf-1",
                "approve-step", "reviewer", ApprovalOutcome.PENDING, now.plusSeconds(3600), now,
                null, null, null, 0);
    }

    private static final class CountingNotifier implements ApprovalNotificationPort {
        int raised;
        int escalated;
        @Override public void notifyRaised(ApprovalTask t) { raised++; }
        @Override public void notifyEscalated(ApprovalTask t) { escalated++; }
    }

    private static final class ThrowingNotifier implements ApprovalNotificationPort {
        @Override public void notifyRaised(ApprovalTask t) { throw new RuntimeException("sink down"); }
        @Override public void notifyEscalated(ApprovalTask t) { throw new RuntimeException("sink down"); }
    }

    @Test
    void fansOutToEverySink() {
        var a = new CountingNotifier();
        var b = new CountingNotifier();
        var composite = new CompositeApprovalNotifier(List.of(a, b));

        composite.notifyRaised(task());
        composite.notifyEscalated(task());

        assertThat(a.raised).isEqualTo(1);
        assertThat(a.escalated).isEqualTo(1);
        assertThat(b.raised).isEqualTo(1);
        assertThat(b.escalated).isEqualTo(1);
    }

    @Test
    void throwingSinkDoesNotSuppressOthers() {
        var healthy = new CountingNotifier();
        // the throwing sink is first, so if it were not isolated, healthy would never be called
        var composite = new CompositeApprovalNotifier(List.of(new ThrowingNotifier(), healthy));

        assertThatCode(() -> composite.notifyRaised(task())).doesNotThrowAnyException();
        assertThatCode(() -> composite.notifyEscalated(task())).doesNotThrowAnyException();

        assertThat(healthy.raised).isEqualTo(1);
        assertThat(healthy.escalated).isEqualTo(1);
    }

    @Test
    void nullDelegateList_isNoOp() {
        var composite = new CompositeApprovalNotifier(null);
        assertThatCode(() -> {
            composite.notifyRaised(task());
            composite.notifyEscalated(task());
        }).doesNotThrowAnyException();
    }
}
