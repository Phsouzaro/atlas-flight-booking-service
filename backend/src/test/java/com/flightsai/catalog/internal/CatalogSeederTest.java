package com.flightsai.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.flightsai.catalog.FlightSearchApi;
import com.flightsai.catalog.FlightSearchCriteria;
import com.flightsai.catalog.FlightView;
import com.flightsai.catalog.internal.CatalogSeedData.SeedFlight;

/** O seeder do perfil local roda na subida; aqui ele roda de novo para provar que é idempotente. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("local")
class CatalogSeederTest {

    static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    static final LocalDate TODAY = LocalDate.of(2026, 3, 10);
    static final int FLIGHTS_PER_DAY = 40;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    CatalogSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    FlightSearchApi catalog;

    @Test
    void seedsSixtyDaysAndIsIdempotent() {
        assertCounts();

        seeder.run(null);

        assertCounts();
    }

    @Test
    void generatesTheSameCatalogForTheSameDate() {
        List<SeedFlight> first = CatalogSeedData.flights(TODAY, CatalogSeedData.DAYS);
        List<SeedFlight> second = CatalogSeedData.flights(TODAY, CatalogSeedData.DAYS);

        assertThat(first).isEqualTo(second);
        // a mesma data gera o mesmo voo, mesmo começando a janela em outro dia
        assertThat(CatalogSeedData.flights(TODAY.plusDays(1), 1))
                .isEqualTo(first.subList(FLIGHTS_PER_DAY, 2 * FLIGHTS_PER_DAY));
        assertThat(first).allSatisfy(f -> {
            assertThat(f.origin()).isNotEqualTo(f.destination());
            assertThat(f.arrivalAt()).isAfter(f.departureAt());
            assertThat(f.fares()).hasSize(3);
        });
    }

    @Test
    void seededCatalogIsSearchable() {
        List<FlightView> flights = catalog.search(new FlightSearchCriteria("CGH", "SDU", TODAY.plusDays(7), 1));

        assertThat(flights).isNotEmpty().allSatisfy(f -> {
            assertThat(f.origin().code()).isEqualTo("CGH");
            assertThat(f.destination().code()).isEqualTo("SDU");
            assertThat(f.departureAt().toLocalDate()).isEqualTo(TODAY.plusDays(7));
        });
    }

    private void assertCounts() {
        assertThat(count("airports")).isEqualTo(12);
        assertThat(count("flights")).isEqualTo(CatalogSeedData.DAYS * FLIGHTS_PER_DAY);
        assertThat(count("seat_inventory")).isEqualTo(CatalogSeedData.DAYS * FLIGHTS_PER_DAY * 3);
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
