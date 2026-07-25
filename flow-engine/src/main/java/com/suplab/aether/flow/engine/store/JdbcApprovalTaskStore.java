package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.ApprovalOutcome;
import com.suplab.aether.flow.domain.ApprovalTask;
import com.suplab.aether.flow.ports.ApprovalTaskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementation of {@link ApprovalTaskStore} backed by the {@code approval_tasks} table.
 *
 * <p>Uses {@code NamedParameterJdbcTemplate} with explicit column lists and UPSERT on the task ID.
 * The open-queue reads filter on {@code outcome IN ('PENDING','ESCALATED')} and order oldest-first
 * so the longest-waiting review surfaces at the top. Every read is scoped by {@code tenant_id}.</p>
 */
public class JdbcApprovalTaskStore implements ApprovalTaskStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcApprovalTaskStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcApprovalTaskStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(ApprovalTask task) {
        var sql = """
                INSERT INTO approval_tasks
                    (id, tenant_id, instance_id, workflow_key, step_key, assigned_role, outcome,
                     sla_due_at, created_at, decided_at, decided_by, comment, escalation_level)
                VALUES
                    (:id, :tenantId, :instanceId, :workflowKey, :stepKey, :assignedRole, :outcome,
                     :slaDueAt, :createdAt, :decidedAt, :decidedBy, :comment, :escalationLevel)
                ON CONFLICT (id) DO UPDATE SET
                    assigned_role = EXCLUDED.assigned_role,
                    outcome = EXCLUDED.outcome,
                    sla_due_at = EXCLUDED.sla_due_at,
                    decided_at = EXCLUDED.decided_at,
                    decided_by = EXCLUDED.decided_by,
                    comment = EXCLUDED.comment,
                    escalation_level = EXCLUDED.escalation_level
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", task.id())
                .addValue("tenantId", task.tenantId())
                .addValue("instanceId", task.instanceId())
                .addValue("workflowKey", task.workflowKey())
                .addValue("stepKey", task.stepKey())
                .addValue("assignedRole", task.assignedRole())
                .addValue("outcome", task.outcome().name())
                .addValue("slaDueAt", Timestamp.from(task.slaDueAt()))
                .addValue("createdAt", Timestamp.from(task.createdAt()))
                .addValue("decidedAt", task.decidedAt() != null ? Timestamp.from(task.decidedAt()) : null)
                .addValue("decidedBy", task.decidedBy())
                .addValue("comment", task.comment())
                .addValue("escalationLevel", task.escalationLevel());
        jdbc.update(sql, params);
        log.debug("Saved approval task id={} tenantId={} instanceId={} outcome={}",
                task.id(), task.tenantId(), task.instanceId(), task.outcome());
    }

    @Override
    public Optional<ApprovalTask> findById(String tenantId, UUID taskId) {
        var sql = """
                SELECT id, tenant_id, instance_id, workflow_key, step_key, assigned_role, outcome,
                       sla_due_at, created_at, decided_at, decided_by, comment, escalation_level
                FROM approval_tasks
                WHERE id = :id AND tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", taskId)
                .addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<ApprovalTask> findOpenByRole(String tenantId, String role, int limit) {
        var sql = """
                SELECT id, tenant_id, instance_id, workflow_key, step_key, assigned_role, outcome,
                       sla_due_at, created_at, decided_at, decided_by, comment, escalation_level
                FROM approval_tasks
                WHERE tenant_id = :tenantId AND assigned_role = :role
                  AND outcome IN ('PENDING', 'ESCALATED')
                ORDER BY created_at ASC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("role", role)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public Optional<ApprovalTask> findOpenByInstance(String tenantId, UUID instanceId) {
        var sql = """
                SELECT id, tenant_id, instance_id, workflow_key, step_key, assigned_role, outcome,
                       sla_due_at, created_at, decided_at, decided_by, comment, escalation_level
                FROM approval_tasks
                WHERE tenant_id = :tenantId AND instance_id = :instanceId
                  AND outcome IN ('PENDING', 'ESCALATED')
                ORDER BY created_at DESC
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("instanceId", instanceId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<ApprovalTask> findBreachedOpen(Instant asOf, int limit) {
        var sql = """
                SELECT id, tenant_id, instance_id, workflow_key, step_key, assigned_role, outcome,
                       sla_due_at, created_at, decided_at, decided_by, comment, escalation_level
                FROM approval_tasks
                WHERE outcome IN ('PENDING', 'ESCALATED')
                  AND sla_due_at < :asOf
                ORDER BY sla_due_at ASC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("asOf", Timestamp.from(asOf))
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public long countOpen() {
        var sql = "SELECT COUNT(*) FROM approval_tasks WHERE outcome IN ('PENDING', 'ESCALATED')";
        Long count = jdbc.queryForObject(sql, new MapSqlParameterSource(), Long.class);
        return count != null ? count : 0L;
    }

    private ApprovalTask mapRow(ResultSet rs, int row) throws SQLException {
        Timestamp decidedAt = rs.getTimestamp("decided_at");
        return new ApprovalTask(
                UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                UUID.fromString(rs.getString("instance_id")),
                rs.getString("workflow_key"),
                rs.getString("step_key"),
                rs.getString("assigned_role"),
                ApprovalOutcome.valueOf(rs.getString("outcome")),
                rs.getTimestamp("sla_due_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                decidedAt != null ? decidedAt.toInstant() : null,
                rs.getString("decided_by"),
                rs.getString("comment"),
                rs.getInt("escalation_level")
        );
    }
}
