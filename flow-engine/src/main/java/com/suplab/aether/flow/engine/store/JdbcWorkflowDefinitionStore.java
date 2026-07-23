package com.suplab.aether.flow.engine.store;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suplab.aether.flow.domain.FlowScope;
import com.suplab.aether.flow.domain.WorkflowDefinition;
import com.suplab.aether.flow.domain.WorkflowStep;
import com.suplab.aether.flow.ports.WorkflowDefinitionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link WorkflowDefinitionStore} backed by the {@code workflow_definitions}
 * table.
 *
 * <p>The ordered step graph is stored as a JSONB {@code steps} column, serialised with Jackson.
 * Uses {@code NamedParameterJdbcTemplate} with explicit column lists and UPSERT on the definition
 * ID. A single row per {@code (tenant_id, workflow_key)} is marked {@code active = true}; reads for
 * the active definition filter on it. Every read and write is scoped by {@code tenant_id} (and
 * {@code workflow_key} where the scope demands it).</p>
 */
public class JdbcWorkflowDefinitionStore implements WorkflowDefinitionStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowDefinitionStore.class);
    private static final TypeReference<List<WorkflowStep>> STEP_LIST = new TypeReference<>() {};

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcWorkflowDefinitionStore(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(WorkflowDefinition definition) {
        var sql = """
                INSERT INTO workflow_definitions
                    (id, tenant_id, workflow_key, name, version, steps, active, created_at, updated_at)
                VALUES
                    (:id, :tenantId, :workflowKey, :name, :version, CAST(:steps AS jsonb), :active,
                     :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    version = EXCLUDED.version,
                    steps = EXCLUDED.steps,
                    active = EXCLUDED.active,
                    updated_at = EXCLUDED.updated_at
                """;
        var params = new MapSqlParameterSource()
                .addValue("id", definition.id())
                .addValue("tenantId", definition.tenantId())
                .addValue("workflowKey", definition.workflowKey())
                .addValue("name", definition.name())
                .addValue("version", definition.version())
                .addValue("steps", writeSteps(definition.steps()))
                .addValue("active", definition.active())
                .addValue("createdAt", Timestamp.from(definition.createdAt()))
                .addValue("updatedAt", Timestamp.from(definition.updatedAt()));
        jdbc.update(sql, params);
        log.debug("Saved definition id={} tenantId={} workflowKey={} version={} active={}",
                definition.id(), definition.tenantId(), definition.workflowKey(), definition.version(),
                definition.active());
    }

    @Override
    public Optional<WorkflowDefinition> findActive(FlowScope scope) {
        var sql = """
                SELECT id, tenant_id, workflow_key, name, version, steps, active, created_at, updated_at
                FROM workflow_definitions
                WHERE tenant_id = :tenantId AND workflow_key = :workflowKey AND active = TRUE
                ORDER BY version DESC
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("workflowKey", scope.workflowKey());
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public Optional<WorkflowDefinition> findByVersion(FlowScope scope, int version) {
        var sql = """
                SELECT id, tenant_id, workflow_key, name, version, steps, active, created_at, updated_at
                FROM workflow_definitions
                WHERE tenant_id = :tenantId AND workflow_key = :workflowKey AND version = :version
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("workflowKey", scope.workflowKey())
                .addValue("version", version);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public List<WorkflowDefinition> findByTenant(String tenantId, int limit) {
        var sql = """
                SELECT id, tenant_id, workflow_key, name, version, steps, active, created_at, updated_at
                FROM workflow_definitions
                WHERE tenant_id = :tenantId
                ORDER BY updated_at DESC
                LIMIT :limit
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", tenantId)
                .addValue("limit", limit);
        return jdbc.query(sql, params, this::mapRow);
    }

    @Override
    public void delete(FlowScope scope) {
        var sql = """
                DELETE FROM workflow_definitions
                WHERE tenant_id = :tenantId AND workflow_key = :workflowKey
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", scope.tenantId())
                .addValue("workflowKey", scope.workflowKey());
        int deleted = jdbc.update(sql, params);
        log.debug("Deleted {} definition row(s) tenantId={} workflowKey={}",
                deleted, scope.tenantId(), scope.workflowKey());
    }

    private WorkflowDefinition mapRow(ResultSet rs, int row) throws SQLException {
        return new WorkflowDefinition(
                java.util.UUID.fromString(rs.getString("id")),
                rs.getString("tenant_id"),
                rs.getString("workflow_key"),
                rs.getString("name"),
                rs.getInt("version"),
                readSteps(rs.getString("steps")),
                rs.getBoolean("active"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String writeSteps(List<WorkflowStep> steps) {
        try {
            return objectMapper.writeValueAsString(steps);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise workflow steps", e);
        }
    }

    private List<WorkflowStep> readSteps(String json) {
        try {
            return objectMapper.readValue(json, STEP_LIST);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to deserialise workflow steps", e);
        }
    }
}
