package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.SlaPolicy;
import com.suplab.aether.flow.ports.SlaPolicyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation of {@link SlaPolicyStore} backed by the {@code tenant_sla_policy} table.
 *
 * <p>One row per tenant; {@link #save} upserts on {@code tenant_id}. The escalation chain is stored
 * as an ordered, comma-separated list of roles ({@code ''} = empty chain). Explicit column lists and
 * named parameters throughout.</p>
 */
public class JdbcSlaPolicyStore implements SlaPolicyStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcSlaPolicyStore.class);

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcSlaPolicyStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SlaPolicy> find(String tenantId) {
        var sql = """
                SELECT tenant_id, default_sla_minutes, escalation_chain
                FROM tenant_sla_policy
                WHERE tenant_id = :tenantId
                """;
        var params = new MapSqlParameterSource().addValue("tenantId", tenantId);
        return jdbc.query(sql, params, this::mapRow).stream().findFirst();
    }

    @Override
    public void save(SlaPolicy policy) {
        var sql = """
                INSERT INTO tenant_sla_policy (tenant_id, default_sla_minutes, escalation_chain, updated_at)
                VALUES (:tenantId, :defaultSlaMinutes, :escalationChain, NOW())
                ON CONFLICT (tenant_id) DO UPDATE SET
                    default_sla_minutes = EXCLUDED.default_sla_minutes,
                    escalation_chain = EXCLUDED.escalation_chain,
                    updated_at = NOW()
                """;
        var params = new MapSqlParameterSource()
                .addValue("tenantId", policy.tenantId())
                .addValue("defaultSlaMinutes", policy.defaultSlaMinutes())
                .addValue("escalationChain", String.join(",", policy.escalationChain()));
        jdbc.update(sql, params);
        log.info("Saved SLA policy tenantId={} defaultSlaMinutes={} chainLength={}",
                policy.tenantId(), policy.defaultSlaMinutes(), policy.escalationChain().size());
    }

    private SlaPolicy mapRow(ResultSet rs, int row) throws SQLException {
        var chain = rs.getString("escalation_chain");
        List<String> roles = (chain == null || chain.isBlank()) ? List.of() : List.of(chain.split(","));
        return new SlaPolicy(rs.getString("tenant_id"), rs.getInt("default_sla_minutes"), roles);
    }
}
