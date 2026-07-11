package com.suplab.aether.flow.api.escalation;

import com.suplab.aether.flow.ports.SlaEscalationPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs the SLA escalation sweep on a schedule and records metrics.
 *
 * <p>Delegates the work to {@link SlaEscalationPort} (set-based SQL) and publishes:</p>
 * <ul>
 *   <li>{@code aether.flow.escalation.escalated} — counter, tasks escalated (accumulates)</li>
 *   <li>{@code aether.flow.approvals.open} — gauge, open approval tasks after the last run</li>
 * </ul>
 */
public class SlaEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaEscalationScheduler.class);

    private final SlaEscalationPort escalationPort;
    private final Counter escalatedCounter;
    private final AtomicLong openGauge = new AtomicLong(0);

    public SlaEscalationScheduler(SlaEscalationPort escalationPort, MeterRegistry meterRegistry) {
        this.escalationPort = escalationPort;
        this.escalatedCounter = Counter.builder("aether.flow.escalation.escalated")
                .description("Total approval tasks escalated across SLA sweeps")
                .register(meterRegistry);
        meterRegistry.gauge("aether.flow.approvals.open", openGauge);
    }

    /**
     * Executes one escalation sweep and updates metrics. Cron is configurable via
     * {@code aether.flow.escalation.cron} (default every 5 minutes).
     */
    @Scheduled(cron = "${aether.flow.escalation.cron:0 */5 * * * *}")
    public void runScheduledSweep() {
        var result = escalationPort.sweep();
        escalatedCounter.increment(result.escalatedCount());
        openGauge.set(result.totalOpen());
        log.info("Scheduled SLA escalation sweep: scanned={} escalated={} totalOpen={}",
                result.scannedCount(), result.escalatedCount(), result.totalOpen());
    }
}
