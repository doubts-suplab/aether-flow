package com.suplab.aether.flow.engine.gateway;

import com.suplab.aether.flow.domain.DeferralOutcome;
import com.suplab.aether.flow.ports.GridOutcomePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Logging {@link GridOutcomePort} — the standalone default that records a deferral's terminal outcome
 * at INFO without calling out to Grid.
 *
 * <p>Wired whenever no Grid callback URL is configured, so Flow reports outcomes observably while
 * running without Grid present. The config-gated {@link HttpGridOutcomeNotifier} replaces it when
 * {@code aether.flow.grid.callback-url} is set.</p>
 */
public class LoggingGridOutcomeNotifier implements GridOutcomePort {

    private static final Logger log = LoggerFactory.getLogger(LoggingGridOutcomeNotifier.class);

    @Override
    public void reportOutcome(DeferralOutcome outcome) {
        log.info("Grid deferral resolved correlationId={} tenantId={} decision={} decidedBy={} (no callback configured)",
                outcome.correlationId(), outcome.tenantId(), outcome.decision(), outcome.decidedBy());
    }
}
