package com.flightsai.catalog.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import jakarta.persistence.EntityManagerFactory;

/**
 * Busca de voos contra um PostgreSQL real, com relógio fixo em 2026-03-10 09:00 de São Paulo.
 * Cada teste parte do mesmo catálogo pequeno, montado em {@link #fixture()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=WARN"})
class CatalogSearchTest {

    static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

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
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    EntityManagerFactory emf;

    UUID morningFlight;

    @BeforeEach
    void fixture() {
        jdbc.execute("TRUNCATE flights, airports CASCADE");
        airport("GIG", "Galeão", "Rio de Janeiro", "BR", "America/Sao_Paulo");
        airport("GRU", "Guarulhos", "São Paulo", "BR", "America/Sao_Paulo");
        airport("LIS", "Humberto Delgado", "Lisboa", "PT", "Europe/Lisbon");

        // dia 15, horário de São Paulo (-03:00)
        morningFlight = flight("AT100", "2026-03-15T08:00", 50, 2, 0);
        flight("AT102", "2026-03-15T23:30", 1, 0, 0);   // 02:30 UTC do dia 16, mas é dia 15 na origem
        flight("AT104", "2026-03-16T00:30", 10, 10, 10); // 03:30 UTC do dia 16
        flight("AT110", "2026-03-15T12:00", 0, 0, 0);    // lotado
        // hoje: um já decolou (08:00 local = 11:00 UTC, antes do relógio) e outro ainda não
        flight("AT106", "2026-03-10T08:00", 10, 10, 10);
        flight("AT108", "2026-03-10T18:00", 10, 10, 10);
    }

    @Test
    void searchesByLocalDayOfOriginOrderedByDeparture() throws Exception {
        search("GRU", "GIG", "2026-03-15", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].flightNumber", contains("AT100", "AT102")))
                .andExpect(jsonPath("$[0].departureAt").value("2026-03-15T08:00:00-03:00"))
                .andExpect(jsonPath("$[0].arrivalAt").value("2026-03-15T09:05:00-03:00"))
                .andExpect(jsonPath("$[0].durationMinutes").value(65))
                .andExpect(jsonPath("$[0].origin.code").value("GRU"))
                .andExpect(jsonPath("$[0].destination.city").value("Rio de Janeiro"))
                .andExpect(jsonPath("$[0].fares[*].fareClass", contains("ECONOMY_LIGHT", "ECONOMY_FLEX")))
                .andExpect(jsonPath("$[0].fares[0].name").value("Econômica Light"))
                .andExpect(jsonPath("$[0].fares[0].cabin").value("ECONOMY"))
                .andExpect(jsonPath("$[0].fares[0].availableSeats").value(50))
                .andExpect(jsonPath("$[0].fares[0].price.amount").value(399.90))
                .andExpect(jsonPath("$[0].fares[0].price.currency").value("BRL"));

        search("GRU", "GIG", "2026-03-16", 1)
                .andExpect(jsonPath("$[*].flightNumber", contains("AT104")));
    }

    @Test
    void acceptsLowercaseCodesAndDefaultsToOnePassenger() throws Exception {
        mvc.perform(get("/flights").with(jwt())
                        .param("origin", "gru").param("destination", "gig").param("date", "2026-03-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void hidesFlightsAndFaresWithoutSeatsForAllPassengers() throws Exception {
        search("GRU", "GIG", "2026-03-15", 2)
                .andExpect(jsonPath("$[*].flightNumber", contains("AT100")))
                .andExpect(jsonPath("$[0].fares[*].fareClass", contains("ECONOMY_LIGHT", "ECONOMY_FLEX")));

        search("GRU", "GIG", "2026-03-15", 3)
                .andExpect(jsonPath("$[*].flightNumber", contains("AT100")))
                .andExpect(jsonPath("$[0].fares[*].fareClass", contains("ECONOMY_LIGHT")));

        search("GRU", "GIG", "2026-03-15", 9)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].flightNumber", contains("AT100")));
    }

    @Test
    void returnsEmptyListWhenNoFlightHasEnoughSeats() throws Exception {
        jdbc.update("UPDATE seat_inventory SET available_seats = LEAST(available_seats, 3)");

        search("GRU", "GIG", "2026-03-15", 4)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void skipsFlightsThatAlreadyDepartedToday() throws Exception {
        search("GRU", "GIG", "2026-03-10", 1)
                .andExpect(jsonPath("$[*].flightNumber", contains("AT108")));
    }

    @Test
    void returnsEmptyListForRouteWithoutFlights() throws Exception {
        search("GRU", "LIS", "2026-03-15", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsSameOriginAndDestination() throws Exception {
        search("GRU", "gru", "2026-03-15", 1)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Origin and destination must be different"));
    }

    @Test
    void rejectsUnknownAirport() throws Exception {
        search("GRU", "XYZ", "2026-03-15", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Unknown airport: XYZ"));
    }

    @Test
    void rejectsPastDate() throws Exception {
        search("GRU", "GIG", "2026-03-09", 1)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Date must not be in the past"));
    }

    @Test
    void rejectsInvalidParameters() throws Exception {
        search("GRU", "GIG", "2026-03-15", 0).andExpect(status().isBadRequest());
        search("GRU", "GIG", "2026-03-15", 10).andExpect(status().isBadRequest());
        search("GR", "GIG", "2026-03-15", 1).andExpect(status().isBadRequest());
        search("GRU", "GIG", "15/03/2026", 1).andExpect(status().isBadRequest());
        mvc.perform(get("/flights").with(jwt()).param("origin", "GRU"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void searchDoesNotQueryPerFlight() throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        search("GRU", "GIG", "2026-03-16", 1).andExpect(jsonPath("$", hasSize(1)));
        long oneFlight = stats.getPrepareStatementCount();

        flight("AT112", "2026-03-16T09:00", 10, 10, 10);
        flight("AT114", "2026-03-16T15:00", 10, 10, 10);
        stats.clear();
        search("GRU", "GIG", "2026-03-16", 1).andExpect(jsonPath("$", hasSize(3)));

        // aeroporto de origem, aeroporto de destino e os voos com tudo junto
        assertThat(stats.getPrepareStatementCount()).isEqualTo(oneFlight).isLessThanOrEqualTo(3);
    }

    @Test
    void returnsFlightDetailsWithAllFares() throws Exception {
        mvc.perform(get("/flights/{id}", morningFlight).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flightNumber").value("AT100"))
                .andExpect(jsonPath("$.origin.name").value("Guarulhos"))
                .andExpect(jsonPath("$.fares[*].fareClass", contains("ECONOMY_LIGHT", "ECONOMY_FLEX", "BUSINESS")))
                .andExpect(jsonPath("$.fares[2].availableSeats").value(0))
                .andExpect(jsonPath("$.fares[2].price.amount").value(1279.90));
    }

    @Test
    void returnsNotFoundForUnknownFlight() throws Exception {
        mvc.perform(get("/flights/{id}", UUID.randomUUID()).with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));

        mvc.perform(get("/flights/nao-e-uuid").with(jwt())).andExpect(status().isBadRequest());
    }

    @Test
    void listsAirportsByCode() throws Exception {
        mvc.perform(get("/airports").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", contains("GIG", "GRU", "LIS")))
                .andExpect(jsonPath("$[2].timeZone").value("Europe/Lisbon"));
    }

    @Test
    void catalogRequiresAuthentication() throws Exception {
        mvc.perform(get("/airports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/flights").param("origin", "GRU").param("destination", "GIG").param("date", "2026-03-15"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/flights/{id}", morningFlight)).andExpect(status().isUnauthorized());
    }

    private ResultActions search(String origin, String destination, String date, int pax) throws Exception {
        return mvc.perform(get("/flights").with(jwt())
                .param("origin", origin)
                .param("destination", destination)
                .param("date", date)
                .param("pax", String.valueOf(pax)));
    }

    private void airport(String code, String name, String city, String country, String zone) {
        jdbc.update("INSERT INTO airports (code, name, city, country_code, time_zone) VALUES (?, ?, ?, ?, ?)",
                code, name, city, country, zone);
    }

    /** Voo GRU → GIG de 65 minutos; horário local de São Paulo e assentos por classe. */
    private UUID flight(String number, String localDeparture, int light, int flex, int business) {
        UUID id = UUID.randomUUID();
        var departure = LocalDateTime.parse(localDeparture).atZone(SAO_PAULO).toOffsetDateTime();
        jdbc.update("INSERT INTO flights (id, flight_number, origin_code, destination_code, departure_at, arrival_at) "
                + "VALUES (?, ?, 'GRU', 'GIG', ?, ?)", id, number, departure, departure.plusMinutes(65));
        seats(id, "ECONOMY_LIGHT", light, "399.90");
        seats(id, "ECONOMY_FLEX", flex, "559.90");
        seats(id, "BUSINESS", business, "1279.90");
        return id;
    }

    private void seats(UUID flightId, String fareClass, int available, String price) {
        jdbc.update("INSERT INTO seat_inventory (id, flight_id, fare_class_code, total_seats, available_seats, "
                        + "price_amount, currency) VALUES (?, ?, ?, 50, ?, ?, 'BRL')",
                UUID.randomUUID(), flightId, fareClass, available, new BigDecimal(price));
    }
}
