package com.flightsai.booking.internal;

import static com.flightsai.booking.internal.BookingTestData.DEPARTURE;
import static com.flightsai.booking.internal.BookingTestData.NOW;
import static com.flightsai.booking.internal.BookingTestData.available;
import static com.flightsai.booking.internal.BookingTestData.holdBody;
import static com.flightsai.booking.internal.BookingTestData.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightsai.shared.MutableClock;
import com.jayway.jsonpath.JsonPath;

/**
 * Requisições simultâneas de verdade (várias threads, pool de conexões real) disputando assento.
 * Prova o ADR-004: o update condicional nunca deixa passar mais reservas do que assentos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "app.security.jwt.secret=test-secret-with-at-least-32-bytes-0123456789",
        "app.booking.expiration-job-enabled=false"})
class BookingConcurrencyTest {

    static final int THREADS = 12;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    @TestConfiguration
    static class Clocks {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(NOW);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void fixture() {
        BookingTestData.reset(jdbc);
    }

    @Test
    void onlyOneRequestGetsTheLastSeat() throws Exception {
        UUID flight = BookingTestData.flight(jdbc, "AT100", DEPARTURE, 1);

        List<MockHttpServletResponse> responses = concurrently(THREADS,
                i -> hold(UUID.randomUUID(), "k-" + i, holdBody(flight, 1)));

        assertThat(statusCounts(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 1L, 409, (long) THREADS - 1));
        assertThat(available(jdbc, flight)).isZero();
        assertThat(count("bookings")).isEqualTo(1);
    }

    @Test
    void neverSellsMoreSeatsThanAvailable() throws Exception {
        UUID flight = BookingTestData.flight(jdbc, "AT100", DEPARTURE, 5);

        // 12 pedidos de 2 assentos para 5 assentos: no máximo 2 passam e sobra 1
        List<MockHttpServletResponse> responses = concurrently(THREADS,
                i -> hold(UUID.randomUUID(), "k-" + i, holdBody(flight, 2)));

        assertThat(statusCounts(responses)).containsExactlyInAnyOrderEntriesOf(Map.of(201, 2L, 409, (long) THREADS - 2));
        assertThat(available(jdbc, flight)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(seats), 0) FROM booking_items", Long.class)).isEqualTo(4);
    }

    @Test
    void sameKeySentConcurrentlyCreatesOneBooking() throws Exception {
        UUID flight = BookingTestData.flight(jdbc, "AT100", DEPARTURE, 10);
        UUID me = UUID.randomUUID();

        // cliente que reenviou o mesmo POST várias vezes ao mesmo tempo
        List<MockHttpServletResponse> responses = concurrently(THREADS, i -> hold(me, "same-key", holdBody(flight, 1)));

        assertThat(statusCounts(responses)).containsExactlyEntriesOf(Map.of(201, (long) THREADS));
        assertThat(responses.stream().map(r -> (String) JsonPath.read(body(r), "$.id")).distinct()).hasSize(1);
        assertThat(available(jdbc, flight)).isEqualTo(9);
        assertThat(count("bookings")).isEqualTo(1);
    }

    /** Solta todas as tarefas juntas e espera todas terminarem. */
    private static List<MockHttpServletResponse> concurrently(int n, IndexedCall call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<MockHttpServletResponse>> futures = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int index = i;
                Callable<MockHttpServletResponse> task = () -> {
                    start.await();
                    return call.run(index);
                };
                futures.add(pool.submit(task));
            }
            start.countDown();
            List<MockHttpServletResponse> responses = new ArrayList<>();
            for (Future<MockHttpServletResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
            return responses;
        } finally {
            pool.shutdownNow();
        }
    }

    @FunctionalInterface
    interface IndexedCall {
        MockHttpServletResponse run(int index) throws Exception;
    }

    private MockHttpServletResponse hold(UUID userId, String key, Object body) throws Exception {
        return mvc.perform(post("/bookings").with(user(userId))
                        .header("Idempotency-Key", key)
                        .contentType(APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andReturn().getResponse();
    }

    private static Map<Integer, Long> statusCounts(List<MockHttpServletResponse> responses) {
        return responses.stream().collect(Collectors.groupingBy(MockHttpServletResponse::getStatus, Collectors.counting()));
    }

    private static String body(MockHttpServletResponse response) {
        try {
            return response.getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
}
