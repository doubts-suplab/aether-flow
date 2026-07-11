package com.suplab.aether.flow.api.escalation;

import com.suplab.aether.flow.ports.SlaEscalationPort;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlaEscalationSchedulerTest {

    /** Stub port returning a scripted sequence of results, one per sweep. */
    private static final class ScriptedEscalationPort implements SlaEscalationPort {
        private final Deque<EscalationResult> results;

        ScriptedEscalationPort(EscalationResult... scripted) {
            this.results = new ArrayDeque<>(List.of(scripted));
        }

        @Override
        public EscalationResult sweep() {
            return results.poll();
        }
    }

    @Test
    void sweep_recordsCounterAndGauge() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedEscalationPort(new SlaEscalationPort.EscalationResult(12, 3, 9));
        var scheduler = new SlaEscalationScheduler(port, registry);

        scheduler.runScheduledSweep();

        assertThat(registry.get("aether.flow.escalation.escalated").counter().count()).isEqualTo(3.0);
        assertThat(registry.get("aether.flow.approvals.open").gauge().value()).isEqualTo(9.0);
    }

    @Test
    void sweep_counterAccumulatesButGaugeReflectsLatest() {
        var registry = new SimpleMeterRegistry();
        var port = new ScriptedEscalationPort(
                new SlaEscalationPort.EscalationResult(12, 3, 9),
                new SlaEscalationPort.EscalationResult(9, 2, 7));
        var scheduler = new SlaEscalationScheduler(port, registry);

        scheduler.runScheduledSweep();
        scheduler.runScheduledSweep();

        assertThat(registry.get("aether.flow.escalation.escalated").counter().count()).isEqualTo(5.0);
        assertThat(registry.get("aether.flow.approvals.open").gauge().value()).isEqualTo(7.0);
    }
}
