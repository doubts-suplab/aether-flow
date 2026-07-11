package com.suplab.aether.flow.ports;

import com.suplab.aether.flow.domain.DeferredDecision;
import com.suplab.aether.flow.domain.WorkflowInstance;

/**
 * Port interface for the Aether Grid integration — turning DEFER decisions into approval work.
 *
 * <p>When Grid's confidence gate defers an agent decision to a human, it hands Flow a bounded
 * {@link DeferredDecision}. This gateway converts that into a running workflow instance parked at a
 * human-approval gate: the review queue Grid's deferral needs. The gateway is the single inbound
 * seam from Grid — Flow does not reach into Grid, and the decision projection carries no Grid
 * internals. Implementations live in {@code flow-engine}.</p>
 */
public interface ApprovalGatewayPort {

    /**
     * Accepts a deferred decision from Grid and starts a human-approval workflow for it.
     *
     * @param decision the bounded decision projection handed over by Grid
     * @return the persisted workflow instance parked awaiting human review
     */
    WorkflowInstance accept(DeferredDecision decision);
}
