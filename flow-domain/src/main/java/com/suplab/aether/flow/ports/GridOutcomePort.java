package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.DeferralOutcome;

/**
 * Reports the human decision on a Grid deferral back to Aether Grid — the closing half of the DEFER
 * seam.
 *
 * <p>Grid defers a low-confidence decision to Flow (inbound {@code DeferredDecision}); once a person
 * approves or rejects the parked review, Flow reports the terminal {@link DeferralOutcome} back so Grid
 * can resume the correlated work. The projection is bounded — a correlation id, the tenant, the terminal
 * decision, and who decided — carrying no request internals and no PII, mirroring the inbound
 * projection's discipline.</p>
 *
 * <p>Reporting is <strong>best-effort</strong> and never on the critical path of a human decision: an
 * implementation that cannot reach Grid must fail safe (log and swallow), because the decision is
 * already durably recorded in Flow. The {@link #NO_OP} default keeps Flow fully standalone — it runs
 * without Grid present.</p>
 */
public interface GridOutcomePort {

    /** Reports a terminal deferral decision back to Grid. Implementations must be best-effort. */
    void reportOutcome(DeferralOutcome outcome);

    /** A no-op port — Flow runs standalone with no Grid callback wired. */
    GridOutcomePort NO_OP = outcome -> { };
}
