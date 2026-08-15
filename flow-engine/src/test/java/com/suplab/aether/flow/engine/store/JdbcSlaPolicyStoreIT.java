package com.suplab.aether.flow.engine.store;

import com.suplab.aether.flow.domain.BusinessHours;
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

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.EnumSet;
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
    void businessHours_roundTrip() {
        var tenant = "tenant-" + UUID.randomUUID();
        var hours = new BusinessHours(ZoneId.of("Europe/London"), LocalTime.of(9, 0), LocalTime.of(17, 30),
                EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
        store.save(new SlaPolicy(tenant, 60, List.of("lead")).withBusinessHours(hours));

        var found = store.find(tenant).orElseThrow();
        assertThat(found.hasBusinessHours()).isTrue();
        var stored = found.businessHours();
        assertThat(stored.zone()).isEqualTo(ZoneId.of("Europe/London"));
        assertThat(stored.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(stored.end()).isEqualTo(LocalTime.of(17, 30));
        assertThat(stored.workingDays())
                .containsExactlyInAnyOrder(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
    }

    @Test
    void updatingPolicy_canClearBusinessHours() {
        var tenant = "tenant-" + UUID.randomUUID();
        store.save(new SlaPolicy(tenant, 60, List.of())
                .withBusinessHours(BusinessHours.standard(ZoneId.of("UTC"))));
        assertThat(store.find(tenant).orElseThrow().hasBusinessHours()).isTrue();

        // Re-saving without a calendar nulls the columns (upsert overwrites).
        store.save(new SlaPolicy(tenant, 60, List.of()));
        assertThat(store.find(tenant).orElseThrow().hasBusinessHours()).isFalse();
    }

    @Test
    void find_isEmptyForUnknownTenant() {
        assertThat(store.find("tenant-" + UUID.randomUUID())).isEmpty();
    }
}
