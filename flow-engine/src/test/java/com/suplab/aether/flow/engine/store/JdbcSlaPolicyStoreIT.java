package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.SlaPolicy;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JdbcSlaPolicyStoreIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16"))
            .withDatabaseName("aether_flow_test")
            .withUsername("aether")
            .withPassword("aether");

    private JdbcSlaPolicyStore store;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        store = new JdbcSlaPolicyStore(new NamedParameterJdbcTemplate(dataSource));
    }

    @Test
    void save_thenFind_roundTripsChain() {
        var tenant = "tenant-" + UUID.randomUUID();
        store.save(new SlaPolicy(tenant, 45, List.of("lead", "manager", "executive")));

        var found = store.find(tenant).orElseThrow();
        assertThat(found.defaultSlaMinutes()).isEqualTo(45);
        assertThat(found.escalationChain()).containsExactly("lead", "manager", "executive");
    }

    @Test
    void save_isUpsertPerTenant() {
        var tenant = "tenant-" + UUID.randomUUID();
        store.save(new SlaPolicy(tenant, 45, List.of("lead")));
        store.save(new SlaPolicy(tenant, 15, List.of("manager", "executive")));

        var found = store.find(tenant).orElseThrow();
        assertThat(found.defaultSlaMinutes()).isEqualTo(15);
        assertThat(found.escalationChain()).containsExactly("manager", "executive");
    }

    @Test
    void emptyChainRoundTripsAsEmpty() {
        var tenant = "tenant-" + UUID.randomUUID();
        store.save(new SlaPolicy(tenant, 60, List.of()));
        assertThat(store.find(tenant).orElseThrow().escalationChain()).isEmpty();
    }

    @Test
    void find_isEmptyForUnknownTenant() {
        assertThat(store.find("tenant-" + UUID.randomUUID())).isEmpty();
    }
}
