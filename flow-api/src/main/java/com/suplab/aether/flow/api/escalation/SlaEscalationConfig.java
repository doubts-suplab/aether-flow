package com.suplab.aether.flow.api.escalation;

import com.suplab.aether.flow.ports.SlaEscalationPort;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables and wires the scheduled SLA escalation sweep.
 *
 * <p>Active by default; set {@code aether.flow.escalation.enabled=false} to opt out (for example in
 * environments where a separate job owns escalation). {@code @EnableScheduling} is scoped to this
 * config so the scheduler only activates when escalation is enabled.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aether.flow.escalation.enabled", havingValue = "true", matchIfMissing = true)
public class SlaEscalationConfig {

    @Bean
    public SlaEscalationScheduler slaEscalationScheduler(SlaEscalationPort escalationPort,
                                                         MeterRegistry meterRegistry) {
        return new SlaEscalationScheduler(escalationPort, meterRegistry);
    }
}
