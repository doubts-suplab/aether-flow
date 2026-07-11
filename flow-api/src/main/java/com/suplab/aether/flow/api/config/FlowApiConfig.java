package com.suplab.aether.flow.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.flow.engine.escalation.SlaEscalationService;
import com.suplab.aether.flow.engine.gateway.DefaultApprovalGateway;
import com.suplab.aether.flow.engine.orchestration.DefaultWorkflowOrchestrationService;
import com.suplab.aether.flow.engine.store.JdbcApprovalTaskStore;
import com.suplab.aether.flow.engine.store.JdbcWorkflowDefinitionStore;
import com.suplab.aether.flow.engine.store.JdbcWorkflowInstanceStore;
import com.suplab.aether.flow.ports.ApprovalGatewayPort;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import com.suplab.aether.flow.ports.SlaEscalationPort;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import com.suplab.aether.flow.ports.WorkflowEnginePort;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Spring configuration for Aether Flow API beans.
 *
 * <p>Wires the JDBC stores, the workflow orchestration engine, the Grid DEFER gateway, and the SLA
 * escalation service using constructor injection. All beans are declared here — never via field
 * {@code @Autowired}. The engine adapters are framework-free; only this config knows how to
 * assemble them from the autoconfigured {@link NamedParameterJdbcTemplate} and
 * {@link ObjectMapper}.</p>
 */
@Configuration
public class FlowApiConfig {

    /**
     * Creates the workflow-definition store (JSONB step graph, scoped by tenant + workflow key).
     */
    @Bean
    public WorkflowDefinitionStore workflowDefinitionStore(NamedParameterJdbcTemplate jdbc,
                                                           ObjectMapper objectMapper) {
        return new JdbcWorkflowDefinitionStore(jdbc, objectMapper);
    }

    /**
     * Creates the workflow-instance store — the durable state that survives restarts.
     */
    @Bean
    public WorkflowInstanceStore workflowInstanceStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcWorkflowInstanceStore(jdbc);
    }

    /**
     * Creates the approval-task store — the human review queue.
     */
    @Bean
    public ApprovalTaskStore approvalTaskStore(NamedParameterJdbcTemplate jdbc) {
        return new JdbcApprovalTaskStore(jdbc);
    }

    /**
     * Creates the workflow orchestration engine (the process state machine).
     */
    @Bean
    public WorkflowEnginePort workflowEnginePort(WorkflowDefinitionStore definitionStore,
                                                 WorkflowInstanceStore instanceStore,
                                                 ApprovalTaskStore approvalTaskStore) {
        return new DefaultWorkflowOrchestrationService(definitionStore, instanceStore, approvalTaskStore);
    }

    /**
     * Creates the Grid DEFER gateway — turns deferred decisions into human-approval workflows.
     *
     * @param deferralSlaMinutes SLA budget for a deferral's approval task (default 60)
     */
    @Bean
    public ApprovalGatewayPort approvalGatewayPort(
            WorkflowDefinitionStore definitionStore,
            WorkflowInstanceStore instanceStore,
            ApprovalTaskStore approvalTaskStore,
            @Value("${aether.flow.deferral.sla-minutes:60}") int deferralSlaMinutes) {
        return new DefaultApprovalGateway(definitionStore, instanceStore, approvalTaskStore, deferralSlaMinutes);
    }

    /**
     * Creates the SLA escalation sweep service.
     */
    @Bean
    public SlaEscalationPort slaEscalationPort(NamedParameterJdbcTemplate jdbc) {
        return new SlaEscalationService(jdbc);
    }
}
