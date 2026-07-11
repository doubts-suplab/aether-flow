package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowInstance;
import com.suplab.aether.flow.domain.WorkflowStatus;
import com.suplab.aether.flow.ports.WorkflowInstanceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link WorkflowInstanceStore} backed by the {@code workflow_instances}
 * table.
 *
 * <p>Persisting every state transition here is what makes workflow state durable across restarts.
 * Uses {@code NamedParameterJdbcTemplate} with explicit column lists and UPSERT on the instance ID.
 * Reads are scoped by {@code tenant_id} (and {@code workflow_key} where the scope demands it).</p>
 */
public class JdbcWorkflowInstanceStore implements WorkflowInstanceStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowInstanceStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcWorkflowInstanceStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(WorkflowInstance instance) {
        var sql = """
                INSERT INTO workflow_instances
                    (id, tenant_id, workflow_key, definition_version, business_key, current_step_key,
                     status, started_at, updated_at, completed_at)
                VALUES
                    (:id, :tenantId, :workflowKey, :definitionVersion, :businessKey, :currentStepKey,
                     :status, :startedAt, :updatedAt, :completedAt)
                ON CONFLICT (id) DO UPDATE SET
                    current_step_key = EXCLUDED.current_step_key,
                    status = EXCLUDED.status,
                    updated_at = EXCLUDED.updated_at,
                    completed_at = EXCLUDED.completed_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", instance.id())
                .addValue("tenantId", instance.tenantId())
                .addValue("workflowKey", instance.workflowKey())
                .addValue("definitionVersion", instance.definitionVersion())
                .addValue("businessKey", instance.businessKey())
                .addValue("currentStepKey", instance.currentStepKey())
                .addValue("status", instance.status().name())
                .addValue("startedAt", Timestamp.from(instance.startedAt()))
                .addValue("updatedAt", Timestamp.from(instance.updatedAt()))
                .addValue("completedAt", instance.completedAt() != null
                        ? Timestamp.from(instance.completedAt()) : null);
        jdbc.update(sql, params);
        log.debug("Saved instance id={} tenantId={} workflowKey={} status={} step={}",
                instance.id(), instance.tenantId(), instance.workflowKey(), instance.status(),
                instance.currentStepKey());
    }

    @Override
    public Optional<WorkflowInstance> findById(FlowScope scope, UUID instanceId) {
        var sql = """
                SELECT id, tenant_id, workflow_key, definition_version, business_key, current_step_key,
                       status, started_at, updated_at, completed_at
                FROM workflow_instances
                WHERE id = :id AND tenant_id = :tenantId AND workflow_key = :workflowKey
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", instanceId)
                .addValue("tenantId", scope.tenantId())
                .addValue("workflowKey", scope.workflowKey());
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public Optional<WorkflowInstance> findByTenantAndId(String tenantId, UUID instanceId) {
        var sql = """
                SELECT id, tenant_id, workflow_key, definition_version, business_key, current_step_key,
                       status, started_at, updated_at, completed_at
                FROM workflow_instances
                WHERE id = :id AND tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", instanceId)
                .addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<WorkflowInstance> findByStatus(FlowScope scope, WorkflowStatus status, int limit) {
        var sql = """
                SELECT id, tenant_id, workflow_key, definition_version, business_key, current_step_key,
                       status, started_at, updated_at, completed_at
                FROM workflow_instances
                WHERE tenant_id = :tenantId AND workflow_key = :workflowKey AND status = :status
                ORDER BY updated_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("workflowKey", scope.workflowKey())
                .addValue("status", status.name())
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public long countByStatus(FlowScope scope, WorkflowStatus status) {
        var sql = """
                SELECT COUNT(*) FROM workflow_instances
                WHERE tenant_id = :tenantId AND workflow_key = :workflowKey AND status = :status
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("workflowKey", scope.workflowKey())
                .addValue("status", status.name());
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    private WorkflowInstance mapRow(ResultSet rs, int row) throws SQLException {
        Timestamp completedAt = rs.getTimestamp("completed_at");
        return new WorkflowInstance(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("workflow_key"),
                rs.getInt("definition_version"),
                rs.getString("business_key"),
                rs.getString("current_step_key"),
                WorkflowStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("started_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant(),
                completedAt != null ? completedAt.toInstant() : null
        );
    }
}
